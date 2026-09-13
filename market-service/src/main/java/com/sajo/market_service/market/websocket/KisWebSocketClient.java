package com.sajo.market_service.market.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.market_service.market.client.kis.KisApiClient;
import com.sajo.market_service.market.client.user.UserAccountFeignClient;
import com.sajo.market_service.market.client.user.dto.UserKisTokenResponse;
import com.sajo.market_service.market.config.MarketSchedulerProperties;
import com.sajo.market_service.market.config.MarketWebSocketProperties;
import com.sajo.market_service.market.dto.kis.KisSubscribeRequest;
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

    @Autowired
    public KisWebSocketClient(
            @Qualifier("kisWebSocketTransportClient") WebSocketClient webSocketClient,
            MarketWebSocketProperties properties,
            MarketSchedulerProperties schedulerProperties,
            KisApiClient kisApiClient,
            UserAccountFeignClient userAccountFeignClient,
            ObjectMapper objectMapper,
            @Qualifier("kisWebSocketReconnectScheduler") ScheduledExecutorService reconnectScheduler
    ) {
        this(webSocketClient, properties, kisApiClient, userAccountFeignClient, objectMapper,
                reconnectScheduler, new KisWebSocketReconnectPolicy(properties), schedulerProperties.targetStockCodes());
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

        webSocketClient.execute(new KisMessageListener(), properties.url())
                .whenComplete((session, throwable) -> {
                    if (throwable != null) {
                        log.warn("KIS WebSocket 연결에 실패했습니다. exceptionType={}", throwable.getClass().getSimpleName());
                        scheduleReconnect();
                    }
                });
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

    private void sendSubscribeFrame(WebSocketSession session, String stockCode) {
        try {
            session.sendMessage(new TextMessage(buildSubscribePayload(stockCode)));
            log.info("KIS WebSocket 종목 구독 요청을 보냈습니다. stockCode={}", stockCode);
        } catch (IOException exception) {
            log.warn("KIS WebSocket 종목 구독 요청 전송에 실패했습니다. stockCode={}, exceptionType={}",
                    stockCode, exception.getClass().getSimpleName());
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
        int attempt = reconnectAttempts.getAndIncrement();
        Duration delay = reconnectPolicy.nextDelay(attempt);
        log.info("KIS WebSocket 재연결을 예약합니다. attempt={}, delayMillis={}", attempt + 1, delay.toMillis());
        reconnectScheduler.schedule(this::connect, delay.toMillis(), TimeUnit.MILLISECONDS);
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
