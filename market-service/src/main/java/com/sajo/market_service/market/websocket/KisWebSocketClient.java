package com.sajo.market_service.market.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.market_service.market.client.kis.KisApiClient;
import com.sajo.market_service.market.client.user.UserAccountFeignClient;
import com.sajo.market_service.market.client.user.dto.UserKisTokenResponse;
import com.sajo.market_service.market.config.MarketWebSocketProperties;
import com.sajo.market_service.market.dto.kis.KisSubscribeRequest;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * KIS WebSocket 실시간 체결가 연결을 관리한다.
 *
 * <p>연결 수립 시 approval_key를 새로 발급받아 구독 중인(또는 구독 예정인) 종목을 (재)구독하고,
 * 연결이 끊기면 지수 백오프로 재연결을 예약한다. 수신 메시지의 파싱·정규화·Redis/DB 반영은
 * 이 클래스의 책임이 아니다(추후 단계에서 별도로 처리한다).</p>
 */
@Slf4j
@Component
public class KisWebSocketClient {

    private final WebSocketClient webSocketClient;
    private final MarketWebSocketProperties properties;
    private final KisApiClient kisApiClient;
    private final UserAccountFeignClient userAccountFeignClient;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService reconnectScheduler;
    private final KisWebSocketReconnectPolicy reconnectPolicy;

    private final Set<String> subscribedStockCodes = ConcurrentHashMap.newKeySet();
    private final AtomicReference<WebSocketSession> currentSession = new AtomicReference<>();
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private volatile String currentApprovalKey;
    private volatile boolean shuttingDown = false;
    private final Object sendLock = new Object();

    @Autowired
    public KisWebSocketClient(
            @Qualifier("kisWebSocketTransportClient") WebSocketClient webSocketClient,
            MarketWebSocketProperties properties,
            KisApiClient kisApiClient,
            UserAccountFeignClient userAccountFeignClient,
            ObjectMapper objectMapper,
            @Qualifier("kisWebSocketReconnectScheduler") ScheduledExecutorService reconnectScheduler
    ) {
        this(webSocketClient, properties, kisApiClient, userAccountFeignClient, objectMapper,
                reconnectScheduler, new KisWebSocketReconnectPolicy(properties), properties.targetStockCodes());
    }

    KisWebSocketClient(
            WebSocketClient webSocketClient,
            MarketWebSocketProperties properties,
            KisApiClient kisApiClient,
            UserAccountFeignClient userAccountFeignClient,
            ObjectMapper objectMapper,
            ScheduledExecutorService reconnectScheduler,
            KisWebSocketReconnectPolicy reconnectPolicy,
            Iterable<String> initialTargetStockCodes
    ) {
        this.webSocketClient = webSocketClient;
        this.properties = properties;
        this.kisApiClient = kisApiClient;
        this.userAccountFeignClient = userAccountFeignClient;
        this.objectMapper = objectMapper;
        this.reconnectScheduler = reconnectScheduler;
        this.reconnectPolicy = reconnectPolicy;
        if (initialTargetStockCodes != null) {
            initialTargetStockCodes.forEach(subscribedStockCodes::add);
        }
        if (subscribedStockCodes.isEmpty()) {
            // market.websocket.target-stock-codes가 비어 있으면 연결/재연결은 계속 성공 로그를 남기지만
            // 실제로는 어떤 종목도 구독하지 않는다. subscribe()를 호출하는 운영 코드 경로가 아직 없으므로
            // 이 상태에서는 조용히 "연결만 되고 아무 것도 구독하지 않는" 상태가 되어 운영 중 발견이 어렵다.
            log.warn("KIS WebSocket 구독 대상 종목이 설정되어 있지 않습니다(market.websocket.target-stock-codes). "
                    + "연결에는 성공하더라도 어떤 종목도 구독하지 않습니다.");
        }
    }

    /** 인증정보 조회 → approval_key 발급 → WebSocket 연결을 시도한다. 실패하면 지수 백오프로 재시도를 예약한다. */
    public void connect() {
        UserKisTokenResponse credentials;
        try {
            credentials = userAccountFeignClient.getKisToken(resolveSystemUserId());
        } catch (Exception exception) {
            log.warn("KIS WebSocket 인증정보 조회에 실패했습니다. exceptionType={}", exception.getClass().getSimpleName());
            scheduleReconnect();
            return;
        }
        if (credentials == null) {
            log.warn("KIS WebSocket 인증정보 조회 결과가 비어 있습니다.");
            scheduleReconnect();
            return;
        }

        String approvalKey;
        try {
            approvalKey = kisApiClient.issueApprovalKey(credentials);
        } catch (Exception exception) {
            log.warn("KIS WebSocket 접속키 발급에 실패했습니다. exceptionType={}", exception.getClass().getSimpleName());
            scheduleReconnect();
            return;
        }
        this.currentApprovalKey = approvalKey;

        try {
            webSocketClient.execute(new KisMessageListener(), properties.url())
                    .whenComplete((session, throwable) -> {
                        if (throwable != null) {
                            log.warn("KIS WebSocket 연결에 실패했습니다. exceptionType={}", throwable.getClass().getSimpleName());
                            scheduleReconnect();
                        }
                    });
        } catch (Exception exception) {
            // market.websocket.url이 잘못된 URI 형식인 경우 등 execute() 호출 자체가 동기적으로 던질 수 있다.
            // 이 경로를 잡아두지 않으면 재연결 예약(scheduleReconnect)이 아예 호출되지 않아
            // 재연결 루프가 영구적으로 멈춘다.
            log.warn("KIS WebSocket 연결 시도(handshake) 호출 자체에서 예외가 발생했습니다. exceptionType={}",
                    exception.getClass().getSimpleName());
            scheduleReconnect();
        }
    }

    /** 별도 스레드에서 최초 연결을 시작한다. 기동 스레드를 블로킹하지 않기 위해 기존 재연결 스케줄러를 재사용한다. */
    public void connectAsync() {
        reconnectScheduler.execute(this::connect);
    }

    /** 종목을 구독 대상에 추가한다. 이미 연결되어 있으면 즉시 구독 요청을 보낸다. */
    public void subscribe(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return;
        }
        subscribedStockCodes.add(stockCode);
        WebSocketSession session = currentSession.get();
        if (session != null && session.isOpen()) {
            sendSubscribeFrame(session, stockCode);
        }
    }

    Set<String> subscribedStockCodes() {
        return Set.copyOf(subscribedStockCodes);
    }

    /**
     * 애플리케이션 종료 시 재연결 시도를 멈추고 현재 세션을 정상적으로 닫는다.
     *
     * <p>{@code kisWebSocketReconnectScheduler} 빈은 {@code destroyMethod="shutdown"}으로 정리되는데,
     * 컨텍스트 종료 순서상 스케줄러가 먼저 종료된 뒤 컨테이너가 세션을 닫으면 {@code afterConnectionClosed}가
     * 이미 종료된 executor에 재연결을 예약하려다 {@link RejectedExecutionException}을 던질 수 있다.
     * {@code shuttingDown} 플래그와 {@link #scheduleReconnect()}의 방어 처리로 이를 안전하게 무시한다.</p>
     */
    @PreDestroy
    void shutdown() {
        shuttingDown = true;
        WebSocketSession session = currentSession.getAndSet(null);
        if (session != null && session.isOpen()) {
            try {
                session.close(CloseStatus.NORMAL);
            } catch (IOException exception) {
                log.debug("KIS WebSocket 종료 중 세션 close에 실패했습니다. exceptionType={}",
                        exception.getClass().getSimpleName());
            }
        }
    }

    private UUID resolveSystemUserId() {
        String configuredUserId = properties.systemUserId();
        if (configuredUserId == null || configuredUserId.isBlank()) {
            throw new IllegalStateException("KIS WebSocket 시스템 사용자 설정(market.websocket.system-user-id)이 없습니다.");
        }
        return UUID.fromString(configuredUserId.trim());
    }

    private void resubscribeAll(WebSocketSession session) {
        for (String stockCode : subscribedStockCodes) {
            sendSubscribeFrame(session, stockCode);
        }
    }

    /**
     * 표준 WebSocket 구현체는 같은 세션에 대한 동시 텍스트 전송을 보장하지 않는다.
     * subscribe()(임의 외부 스레드)와 resubscribeAll()(WebSocket 콜백 스레드)이 동시에 호출될 수 있으므로
     * 전송을 직렬화한다.
     */
    private void sendSubscribeFrame(WebSocketSession session, String stockCode) {
        synchronized (sendLock) {
            try {
                session.sendMessage(new TextMessage(buildSubscribePayload(stockCode)));
                log.info("KIS WebSocket 종목 구독 요청을 보냈습니다. stockCode={}", stockCode);
            } catch (IOException exception) {
                log.warn("KIS WebSocket 종목 구독 요청 전송에 실패했습니다. stockCode={}, exceptionType={}",
                        stockCode, exception.getClass().getSimpleName());
            }
        }
    }

    private String buildSubscribePayload(String stockCode) {
        try {
            return objectMapper.writeValueAsString(KisSubscribeRequest.of(currentApprovalKey, stockCode));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("KIS WebSocket 구독 메시지 직렬화에 실패했습니다.", exception);
        }
    }

    private void scheduleReconnect() {
        if (shuttingDown) {
            log.debug("KIS WebSocket이 종료 중이어서 재연결을 예약하지 않습니다.");
            return;
        }
        int attempt = reconnectAttempts.getAndIncrement();
        Duration delay = reconnectPolicy.nextDelay(attempt);
        log.info("KIS WebSocket 재연결을 예약합니다. attempt={}, delayMillis={}", attempt + 1, delay.toMillis());
        try {
            reconnectScheduler.schedule(this::connect, delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException exception) {
            // 애플리케이션 종료 시퀀스에서 kisWebSocketReconnectScheduler 빈이 이 컴포넌트보다 먼저
            // shutdown되는 경합이 발생할 수 있다. 이 경우 재연결은 더 이상 의미가 없으므로 무시한다.
            log.debug("KIS WebSocket 재연결 스케줄러가 이미 종료되어 재연결 예약을 건너뜁니다.");
        }
    }

    /** 연결 생명주기 콜백만 담당한다. 메시지 정규화·저장은 이후 단계(15단계)에서 처리한다. */
    final class KisMessageListener extends TextWebSocketHandler {

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            log.info("KIS WebSocket 연결에 성공했습니다. sessionId={}", session.getId());
            currentSession.set(session);
            reconnectAttempts.set(0);
            resubscribeAll(session);
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            log.debug("KIS WebSocket 메시지를 수신했습니다. payloadLength={}", message.getPayloadLength());
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            log.warn("KIS WebSocket 전송 오류가 발생했습니다. sessionId={}, exceptionType={}",
                    session.getId(), exception.getClass().getSimpleName());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
            log.warn("KIS WebSocket 연결이 종료되었습니다. sessionId={}, closeStatus={}", session.getId(), closeStatus);
            currentSession.compareAndSet(session, null);
            scheduleReconnect();
        }
    }
}
