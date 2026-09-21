package com.sajo.market_service.market.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.market_service.market.client.kis.KisApiClient;
import com.sajo.market_service.market.client.user.KisWebSocketUserAccountFeignClient;
import com.sajo.market_service.market.client.user.dto.UserKisTokenResponse;
import com.sajo.market_service.market.config.MarketWebSocketProperties;
import com.sajo.market_service.market.dto.kis.KisSubscribeRequest;
import com.sajo.market_service.market.service.command.MarketRealtimePriceUpdateService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * KIS WebSocket 실시간 체결가 연결을 관리한다.
 *
 * <p>연결 수립 시 approval_key를 새로 발급받아 구독 중인(또는 구독 예정인) 종목을 (재)구독하고,
 * 연결이 끊기면 지수 백오프로 재연결을 예약한다. 수신 메시지의 파싱·정규화·Redis 반영은
 * {@link MarketRealtimePriceUpdateService}에 위임한다(PostgreSQL 저장은 이 클래스도,
 * 그 서비스도 아닌 별도의 1분 주기 스케줄러 책임이다).</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "market.websocket", name = "enabled", havingValue = "true")
public class KisWebSocketClient {

    private final WebSocketClient webSocketClient;
    private final MarketWebSocketProperties properties;
    private final KisApiClient kisApiClient;
    private final KisWebSocketUserAccountFeignClient userAccountFeignClient;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService reconnectScheduler;
    private final KisWebSocketReconnectPolicy reconnectPolicy;
    private final MarketRealtimePriceUpdateService realtimePriceUpdateService;

    private final Set<String> subscribedStockCodes = ConcurrentHashMap.newKeySet();
    private final AtomicReference<WebSocketSession> currentSession = new AtomicReference<>();
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    // connect() 시도마다 증가하는 세대(generation) 식별자. 핸드셰이크 타임아웃으로 폐기된 시도가 뒤늦게
    // 성공하더라도(예: 네트워크 지연으로 handshakeTimeout을 넘겨 새 시도가 이미 시작된 뒤 원래 시도가
    // 완료), 자신의 세대가 최신 세대와 다르면 즉시 닫아서 이미 맺어진 활성 세션을 덮어쓰지 않게 한다.
    private final AtomicLong connectionGeneration = new AtomicLong(0);
    private volatile String currentApprovalKey;
    private volatile boolean shuttingDown = false;
    private final Object sendLock = new Object();

    @Autowired
    public KisWebSocketClient(
            @Qualifier("kisWebSocketTransportClient") WebSocketClient webSocketClient,
            MarketWebSocketProperties properties,
            KisApiClient kisApiClient,
            KisWebSocketUserAccountFeignClient userAccountFeignClient,
            ObjectMapper objectMapper,
            @Qualifier("kisWebSocketReconnectScheduler") ScheduledExecutorService reconnectScheduler,
            MarketRealtimePriceUpdateService realtimePriceUpdateService
    ) {
        this(webSocketClient, properties, kisApiClient, userAccountFeignClient, objectMapper,
                reconnectScheduler, new KisWebSocketReconnectPolicy(properties), properties.targetStockCodes(),
                realtimePriceUpdateService);
    }

    KisWebSocketClient(
            WebSocketClient webSocketClient,
            MarketWebSocketProperties properties,
            KisApiClient kisApiClient,
            KisWebSocketUserAccountFeignClient userAccountFeignClient,
            ObjectMapper objectMapper,
            ScheduledExecutorService reconnectScheduler,
            KisWebSocketReconnectPolicy reconnectPolicy,
            Iterable<String> initialTargetStockCodes,
            MarketRealtimePriceUpdateService realtimePriceUpdateService
    ) {
        this.webSocketClient = webSocketClient;
        this.properties = properties;
        this.kisApiClient = kisApiClient;
        this.userAccountFeignClient = userAccountFeignClient;
        this.objectMapper = objectMapper;
        this.reconnectScheduler = reconnectScheduler;
        this.reconnectPolicy = reconnectPolicy;
        this.realtimePriceUpdateService = realtimePriceUpdateService;
        if (initialTargetStockCodes != null) {
            initialTargetStockCodes.forEach(subscribedStockCodes::add);
        }
        if (properties.enabled() && subscribedStockCodes.isEmpty()) {
            // market.websocket.target-stock-codes가 비어 있으면 연결/재연결은 계속 성공 로그를 남기지만
            // 실제로는 어떤 종목도 구독하지 않는다. subscribe()를 호출하는 운영 코드 경로가 아직 없으므로
            // 이 상태에서는 조용히 "연결만 되고 아무 것도 구독하지 않는" 상태가 되어 운영 중 발견이 어렵다.
            // enabled=false인 로컬/테스트/CI 환경에서는 어차피 연결을 시도하지 않으므로 경고를 남기지 않는다.
            log.warn("KIS WebSocket 구독 대상 종목이 설정되어 있지 않습니다(market.websocket.target-stock-codes). "
                    + "연결에는 성공하더라도 어떤 종목도 구독하지 않습니다.");
        }
    }

    /** 인증정보 조회 → approval_key 발급 → WebSocket 연결을 시도한다. 실패하면 지수 백오프로 재시도를 예약한다. */
    public void connect() {
        if (shuttingDown) {
            // scheduleReconnect()로 이미 예약된 지연 작업은 shutdown()이 취소하지 않는다. 종료가 시작된
            // 이후에 그 예약이 실행되면, 종료 도중 user-service Feign 호출/KIS approval_key 발급/WebSocket
            // 핸드셰이크를 새로 시도하게 되어 이미 종료 중인 다른 빈에 불필요한 호출이 발생할 수 있다.
            log.debug("KIS WebSocket이 종료 중이어서 예약된 재연결 시도를 건너뜁니다.");
            return;
        }
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

        long generation = connectionGeneration.incrementAndGet();
        try {
            CompletableFuture<WebSocketSession> handshakeFuture =
                    webSocketClient.execute(new KisMessageListener(generation), properties.url());
            handshakeFuture
                    .orTimeout(properties.handshakeTimeout().toMillis(), TimeUnit.MILLISECONDS)
                    .whenComplete((session, throwable) -> {
                        if (throwable != null) {
                            if (throwable instanceof TimeoutException) {
                                // kisWebSocketTransportClient의 IO_TIMEOUT_MS가 먼저 걸리지 않는 경우를 대비한
                                // 2차 방어. 이미 완료된 future에 대한 cancel()은 아무 효과 없이 false를 반환하므로 안전하다.
                                handshakeFuture.cancel(true);
                            }
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

    /**
     * 종목을 구독 대상에 추가한다. 이미 연결되어 있으면 즉시 구독 요청을 보낸다.
     *
     * <p>로컬 상태 갱신과 실제 전송을 {@code sendLock}으로 함께 직렬화한다. 그렇지 않으면 같은
     * 종목에 대해 subscribe()와 unsubscribe()가 거의 동시에 호출될 때, set 반영 순서와 실제 KIS
     * 전송 순서가 어긋나 로컬 상태와 KIS 서버 상태가 서로 다른 값으로 남을 수 있다
     * (예: 로컬은 미구독인데 KIS에는 구독이 살아있는 상태).</p>
     */
    public void subscribe(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return;
        }
        synchronized (sendLock) {
            subscribedStockCodes.add(stockCode);
            WebSocketSession session = currentSession.get();
            if (session != null && session.isOpen()) {
                sendSubscribeFrame(session, stockCode);
            }
        }
    }

    /**
     * 종목을 구독 대상에서 제거한다. 이미 연결되어 있으면 즉시 구독 해제 요청을 보낸다.
     *
     * <p>subscribe()와 동일하게 {@code sendLock}으로 로컬 상태 갱신과 전송을 함께 직렬화해,
     * 같은 종목에 대한 subscribe()/unsubscribe() 동시 호출 시 순서 역전을 방지한다.</p>
     */
    public void unsubscribe(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return;
        }
        synchronized (sendLock) {
            boolean wasSubscribed = subscribedStockCodes.remove(stockCode);
            if (!wasSubscribed) {
                // 구독 중이 아니었던 종목이면 KIS에 해제 요청을 보낼 필요가 없다.
                return;
            }
            WebSocketSession session = currentSession.get();
            if (session != null && session.isOpen()) {
                sendUnsubscribeFrame(session, stockCode);
            }
        }
    }

    /** 구독 중인 종목 코드 스냅샷. MarketRealtimePriceScheduler가 1분 스냅샷 대상 결정에 사용한다. */
    public Set<String> subscribedStockCodes() {
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
        if (session != null) {
            closeQuietly(session);
        }
    }

    private void closeQuietly(WebSocketSession session) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.close(CloseStatus.NORMAL);
        } catch (IOException exception) {
            log.debug("KIS WebSocket 세션 close에 실패했습니다. exceptionType={}",
                    exception.getClass().getSimpleName());
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
        synchronized (sendLock) {
            for (String stockCode : subscribedStockCodes) {
                sendSubscribeFrame(session, stockCode);
            }
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

    /**
     * subscribe()/unsubscribe() 모두 동일한 sendLock으로 전송을 직렬화해 sendSubscribeFrame()과
     * 같은 세션에 대한 동시 텍스트 전송 문제를 피한다.
     */
    private void sendUnsubscribeFrame(WebSocketSession session, String stockCode) {
        synchronized (sendLock) {
            try {
                session.sendMessage(new TextMessage(buildUnsubscribePayload(stockCode)));
                log.info("KIS WebSocket 종목 구독 해제 요청을 보냈습니다. stockCode={}", stockCode);
            } catch (IOException exception) {
                log.warn("KIS WebSocket 종목 구독 해제 요청 전송에 실패했습니다. stockCode={}, exceptionType={}",
                        stockCode, exception.getClass().getSimpleName());
            }
        }
    }

    private String buildSubscribePayload(String stockCode) {
        try {
            String s = objectMapper.writeValueAsString(KisSubscribeRequest.of(currentApprovalKey, stockCode));
            return s;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("KIS WebSocket 구독 메시지 직렬화에 실패했습니다.", exception);
        }
    }

    private String buildUnsubscribePayload(String stockCode) {
        try {
            return objectMapper.writeValueAsString(KisSubscribeRequest.unsubscribeOf(currentApprovalKey, stockCode));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("KIS WebSocket 구독 해제 메시지 직렬화에 실패했습니다.", exception);
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

    /** 연결 생명주기 콜백과 메시지 수신을 담당한다. 수신한 원문의 정규화·Redis 반영은
     * {@link MarketRealtimePriceUpdateService}에 위임하고(수신 스레드 보호를 위해 예외를 흡수),
     * PostgreSQL 저장은 여기서도 그 서비스에서도 하지 않는다({@link com.sajo.market_service.market.scheduler.MarketRealtimePriceScheduler}
     * 책임). */
    final class KisMessageListener extends TextWebSocketHandler {

        /** 이 리스너를 만든 connect() 시도의 세대. {@link #connectionGeneration}과 비교해 폐기 여부를 판단한다. */
        private final long generation;

        KisMessageListener(long generation) {
            this.generation = generation;
        }

        private boolean isDiscarded() {
            return shuttingDown || generation != connectionGeneration.get();
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            if (isDiscarded()) {
                // shutdown()되었거나, 이 시도가 핸드셰이크 타임아웃 등으로 이미 폐기되고 새 시도(더 높은
                // generation)가 시작된 뒤 뒤늦게 연결에 성공한 경우다. currentSession을 건드리지 않고
                // 바로 닫아서, 이미 맺어져 있을 수 있는 활성 세션을 덮어쓰지 않는다.
                log.info("KIS WebSocket 이미 폐기된 시도가 뒤늦게 연결에 성공해 즉시 닫습니다. sessionId={}, generation={}",
                        session.getId(), generation);
                closeQuietly(session);
                return;
            }
            log.info("KIS WebSocket 연결에 성공했습니다. sessionId={}", session.getId());
            // 기본 텍스트 메시지 버퍼(보통 8KB)로는 KIS가 여러 종목을 한 프레임에 이어붙여 보내는 경우를
            // 감당하지 못해 세션이 closeStatus 1009(too big for the output buffer)로 강제 종료되는 것이
            // 실제로 관측되었다. 메시지를 실제로 받기 전에 반드시 먼저 늘려둔다.
            session.setTextMessageSizeLimit(properties.textMessageBufferSize());
            WebSocketSession previous = currentSession.getAndSet(session);
            if (isDiscarded()) {
                // 위 검사와 currentSession 등록 사이에 shutdown() 또는 새 시도(더 높은 generation)가
                // 끼어든 경우다. 그 새 시도가 아직 등록되지 않은 이 세션을 보지 못했을 수 있으므로, 등록
                // 직후 재확인해서 원래 값(previous)으로 되돌리고 우리 세션은 닫는다. 그 사이 currentSession이
                // 이미 다른 값으로 또 바뀌어 있다면(더 최신 시도가 이미 등록을 마쳤다면) 복원을 건너뛴다.
                currentSession.compareAndSet(session, previous);
                log.info("KIS WebSocket 세션 등록 직후 종료/폐기가 감지되어 즉시 닫습니다. sessionId={}", session.getId());
                closeQuietly(session);
                return;
            }
            if (previous != null && previous != session) {
                // 정상 경로라면 비어 있어야 하지만, 방어적으로 남아있는 이전 세션이 있다면 함께 정리한다.
                log.warn("KIS WebSocket 세션 등록 시 이전 세션이 아직 남아있어 함께 정리합니다. previousSessionId={}",
                        previous.getId());
                closeQuietly(previous);
            }
            reconnectAttempts.set(0);
            resubscribeAll(session);
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            log.debug("KIS WebSocket 메시지를 수신했습니다. payloadLength={}", message.getPayloadLength());
            try {
                realtimePriceUpdateService.updateFromRawMessage(message.getPayload());
            } catch (Exception exception) {
                // 정규화/Redis 실패로 이 핸들러 스레드(Tomcat의 WebSocketClient-AsyncIO-*)가 죽으면 안 된다 —
                // 죽으면 이후 메시지도 못 받게 되어 연결이 살아있는데도 시세가 멈춘 것처럼 보인다.
                log.warn("KIS WebSocket 메시지 처리 중 예외가 발생했습니다. exceptionType={}",
                        exception.getClass().getSimpleName());
            }
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            log.warn("KIS WebSocket 전송 오류가 발생했습니다. sessionId={}, exceptionType={}",
                    session.getId(), exception.getClass().getSimpleName());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
            log.warn("KIS WebSocket 연결이 종료되었습니다. sessionId={}, closeStatus={}", session.getId(), closeStatus);
            boolean wasActiveSession = currentSession.compareAndSet(session, null);
            if (!wasActiveSession) {
                // 이미 다른(더 최신) 시도로 대체되었거나, 세션 등록 경합/폐기 처리로 우리가 직접 닫은
                // 세션이다. 활성 연결이 아니므로 중복 재연결을 예약하지 않는다.
                log.debug("KIS WebSocket 활성 세션이 아닌 연결의 종료 통지라 재연결을 예약하지 않습니다. sessionId={}",
                        session.getId());
                return;
            }
            scheduleReconnect();
        }
    }
}
