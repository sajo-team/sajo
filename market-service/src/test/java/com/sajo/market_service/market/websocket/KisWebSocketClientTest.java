package com.sajo.market_service.market.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.market_service.market.client.kis.KisApiClient;
import com.sajo.market_service.market.client.user.UserAccountFeignClient;
import com.sajo.market_service.market.client.user.dto.UserKisTokenResponse;
import com.sajo.market_service.market.config.MarketWebSocketProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class KisWebSocketClientTest {

    private static final String SYSTEM_USER_ID = "11111111-1111-1111-1111-111111111111";

    private final WebSocketClient webSocketClient = mock(WebSocketClient.class);
    private final KisApiClient kisApiClient = mock(KisApiClient.class);
    private final UserAccountFeignClient userAccountFeignClient = mock(UserAccountFeignClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService reconnectScheduler = mock(ScheduledExecutorService.class);
    private final KisWebSocketReconnectPolicy reconnectPolicy = mock(KisWebSocketReconnectPolicy.class);

    @Test
    void connectEstablishesSessionAndSubscribesInitialTargets() throws Exception {
        stubSuccessfulCredentials();
        WebSocketSession session = openSession();
        given(webSocketClient.execute(any(WebSocketHandler.class), anyString()))
                .willReturn(CompletableFuture.completedFuture(session));

        KisWebSocketClient client = client(List.of("005930"));
        client.connect();

        WebSocketHandler handler = capturedHandler();
        handler.afterConnectionEstablished(session);

        verify(session).sendMessage(any(TextMessage.class));
        assertThat(client.subscribedStockCodes()).containsExactly("005930");
    }

    @Test
    void afterConnectionClosedSchedulesReconnectWithPolicyDelay() throws Exception {
        stubSuccessfulCredentials();
        WebSocketSession session = openSession();
        given(webSocketClient.execute(any(WebSocketHandler.class), anyString()))
                .willReturn(CompletableFuture.completedFuture(session));
        given(reconnectPolicy.nextDelay(0)).willReturn(Duration.ofMillis(250));

        KisWebSocketClient client = client(List.of());
        client.connect();
        WebSocketHandler handler = capturedHandler();
        handler.afterConnectionEstablished(session);

        handler.afterConnectionClosed(session, CloseStatus.GOING_AWAY);

        verify(reconnectScheduler).schedule(any(Runnable.class), eq(250L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void repeatedDisconnectsIncreaseAttemptCountPassedToPolicy() throws Exception {
        stubSuccessfulCredentials();
        WebSocketSession session = openSession();
        given(webSocketClient.execute(any(WebSocketHandler.class), anyString()))
                .willReturn(CompletableFuture.completedFuture(session));
        given(reconnectPolicy.nextDelay(0)).willReturn(Duration.ofMillis(100));
        given(reconnectPolicy.nextDelay(1)).willReturn(Duration.ofMillis(200));

        KisWebSocketClient client = client(List.of());
        client.connect();
        WebSocketHandler handler = capturedHandler();
        handler.afterConnectionEstablished(session);

        handler.afterConnectionClosed(session, CloseStatus.GOING_AWAY);
        handler.afterConnectionClosed(session, CloseStatus.GOING_AWAY);

        verify(reconnectScheduler).schedule(any(Runnable.class), eq(100L), eq(TimeUnit.MILLISECONDS));
        verify(reconnectScheduler).schedule(any(Runnable.class), eq(200L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void credentialLookupFailureSchedulesReconnectWithoutOpeningSocket() {
        given(userAccountFeignClient.getKisToken(any())).willThrow(new RuntimeException("user-service down"));
        given(reconnectPolicy.nextDelay(0)).willReturn(Duration.ofMillis(500));

        KisWebSocketClient client = client(List.of());
        client.connect();

        verify(reconnectScheduler).schedule(any(Runnable.class), eq(500L), eq(TimeUnit.MILLISECONDS));
        verify(webSocketClient, never()).execute(any(WebSocketHandler.class), anyString());
    }

    @Test
    void subscribeAfterConnectionEstablishedSendsFrameImmediately() throws Exception {
        stubSuccessfulCredentials();
        WebSocketSession session = openSession();
        given(webSocketClient.execute(any(WebSocketHandler.class), anyString()))
                .willReturn(CompletableFuture.completedFuture(session));

        KisWebSocketClient client = client(List.of());
        client.connect();
        capturedHandler().afterConnectionEstablished(session);

        client.subscribe("000660");

        verify(session).sendMessage(any(TextMessage.class));
        assertThat(client.subscribedStockCodes()).containsExactly("000660");
    }

    @Test
    void handshakeCallThrowingSynchronouslySchedulesReconnect() {
        stubSuccessfulCredentials();
        given(webSocketClient.execute(any(WebSocketHandler.class), anyString()))
                .willThrow(new IllegalArgumentException("malformed URI"));
        given(reconnectPolicy.nextDelay(0)).willReturn(Duration.ofMillis(750));

        KisWebSocketClient client = client(List.of());
        client.connect();

        verify(reconnectScheduler).schedule(any(Runnable.class), eq(750L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void connectAsyncSubmitsConnectToReconnectSchedulerWithoutBlockingCaller() {
        KisWebSocketClient client = client(List.of());

        client.connectAsync();

        verify(reconnectScheduler).execute(any(Runnable.class));
        verify(webSocketClient, never()).execute(any(WebSocketHandler.class), anyString());
    }

    @Test
    void shutdownClosesOpenSessionAndSuppressesFurtherReconnects() throws Exception {
        stubSuccessfulCredentials();
        WebSocketSession session = openSession();
        given(webSocketClient.execute(any(WebSocketHandler.class), anyString()))
                .willReturn(CompletableFuture.completedFuture(session));

        KisWebSocketClient client = client(List.of());
        client.connect();
        WebSocketHandler handler = capturedHandler();
        handler.afterConnectionEstablished(session);

        client.shutdown();

        verify(session).close(CloseStatus.NORMAL);

        // shutdown() 이후 전송 계층이 뒤늦게 afterConnectionClosed를 통지해도 재연결을 예약하지 않는다.
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        verify(reconnectScheduler, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    @Test
    void afterConnectionEstablishedClosesSessionImmediatelyWhenAlreadyShuttingDown() throws Exception {
        stubSuccessfulCredentials();
        WebSocketSession firstSession = openSession();
        given(webSocketClient.execute(any(WebSocketHandler.class), anyString()))
                .willReturn(CompletableFuture.completedFuture(firstSession));

        KisWebSocketClient client = client(List.of());
        client.connect();
        WebSocketHandler handler = capturedHandler();

        // shutdown()이 먼저 호출된 뒤(예: 재연결 스케줄러 스레드에서 진행 중이던 connect() 시도가
        // 뒤늦게 완료되어) afterConnectionEstablished가 호출되는 경합 상황을 재현한다.
        client.shutdown();

        WebSocketSession lateSession = openSession();
        handler.afterConnectionEstablished(lateSession);

        verify(lateSession).close(CloseStatus.NORMAL);
        verify(lateSession, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void scheduleReconnectSwallowsRejectedExecutionWhenSchedulerAlreadyShutDown() {
        given(userAccountFeignClient.getKisToken(any())).willThrow(new RuntimeException("user-service down"));
        given(reconnectPolicy.nextDelay(0)).willReturn(Duration.ofMillis(500));
        given(reconnectScheduler.schedule(any(Runnable.class), eq(500L), eq(TimeUnit.MILLISECONDS)))
                .willThrow(new RejectedExecutionException("scheduler already shut down"));

        KisWebSocketClient client = client(List.of());

        client.connect();
    }

    private void stubSuccessfulCredentials() {
        UserKisTokenResponse credentials = new UserKisTokenResponse("access-token", "app-key", "secret-key");
        given(userAccountFeignClient.getKisToken(any())).willReturn(credentials);
        given(kisApiClient.issueApprovalKey(credentials)).willReturn("approval-key");
    }

    private WebSocketSession openSession() {
        WebSocketSession session = mock(WebSocketSession.class);
        given(session.isOpen()).willReturn(true);
        given(session.getId()).willReturn("session-1");
        return session;
    }

    private WebSocketHandler capturedHandler() {
        ArgumentCaptor<WebSocketHandler> captor = ArgumentCaptor.forClass(WebSocketHandler.class);
        verify(webSocketClient).execute(captor.capture(), anyString());
        return captor.getValue();
    }

    private KisWebSocketClient client(List<String> initialTargetStockCodes) {
        MarketWebSocketProperties properties = new MarketWebSocketProperties(
                true, "ws://localhost:31000", SYSTEM_USER_ID, Duration.ofMillis(10), Duration.ofSeconds(1), 2.0,
                List.of());
        return new KisWebSocketClient(
                webSocketClient,
                properties,
                kisApiClient,
                userAccountFeignClient,
                objectMapper,
                reconnectScheduler,
                reconnectPolicy,
                initialTargetStockCodes
        );
    }
}
