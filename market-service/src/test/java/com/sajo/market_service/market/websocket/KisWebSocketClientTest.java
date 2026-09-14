package com.sajo.market_service.market.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.market_service.market.client.kis.KisApiClient;
import com.sajo.market_service.market.client.user.KisWebSocketUserAccountFeignClient;
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
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class KisWebSocketClientTest {

    private static final String SYSTEM_USER_ID = "11111111-1111-1111-1111-111111111111";

    private final WebSocketClient webSocketClient = mock(WebSocketClient.class);
    private final KisApiClient kisApiClient = mock(KisApiClient.class);
    private final KisWebSocketUserAccountFeignClient userAccountFeignClient = mock(KisWebSocketUserAccountFeignClient.class);
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
    void repeatedDisconnectsAcrossReconnectAttemptsIncreaseAttemptCountPassedToPolicy() throws Exception {
        // afterConnectionClosed는 이제 "활성 세션이었던 연결이 닫혔을 때만" 재연결을 예약하므로(중복/폐기된
        // 세션의 종료 통지로 인한 중복 예약 방지), 같은 세션을 두 번 닫는 것이 아니라 실제로 두 번의
        // 연결 사이클(연결→종료→재연결→연결→종료)을 재현해야 한다.
        stubSuccessfulCredentials();
        WebSocketSession session1 = openSession();
        WebSocketSession session2 = openSession();
        given(webSocketClient.execute(any(WebSocketHandler.class), anyString()))
                .willReturn(CompletableFuture.completedFuture(session1))
                .willReturn(CompletableFuture.completedFuture(session2));
        given(reconnectPolicy.nextDelay(0)).willReturn(Duration.ofMillis(100));
        given(reconnectPolicy.nextDelay(1)).willReturn(Duration.ofMillis(200));

        KisWebSocketClient client = client(List.of());

        client.connect();
        ArgumentCaptor<WebSocketHandler> firstCaptor = ArgumentCaptor.forClass(WebSocketHandler.class);
        verify(webSocketClient, times(1)).execute(firstCaptor.capture(), anyString());
        WebSocketHandler firstHandler = firstCaptor.getValue();
        firstHandler.afterConnectionEstablished(session1);
        firstHandler.afterConnectionClosed(session1, CloseStatus.GOING_AWAY);

        // 실제로는 reconnectScheduler가 예약된 delay 이후 connect()를 실행하지만, 스케줄러가 mock이라
        // 여기서는 "다음 시도"를 직접 시뮬레이션한다.
        client.connect();
        ArgumentCaptor<WebSocketHandler> secondCaptor = ArgumentCaptor.forClass(WebSocketHandler.class);
        verify(webSocketClient, times(2)).execute(secondCaptor.capture(), anyString());
        WebSocketHandler secondHandler = secondCaptor.getValue();
        secondHandler.afterConnectionEstablished(session2);
        secondHandler.afterConnectionClosed(session2, CloseStatus.GOING_AWAY);

        verify(reconnectScheduler).schedule(any(Runnable.class), eq(100L), eq(TimeUnit.MILLISECONDS));
        verify(reconnectScheduler).schedule(any(Runnable.class), eq(200L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void lateHandshakeSuccessFromSupersededAttemptClosesItselfWithoutAffectingActiveSession() throws Exception {
        // handshakeTimeout으로 폐기된 시도(생성 1)의 실제 핸드셰이크가, 그 다음 시도(생성 2)가 이미 정상
        // 연결/재구독을 마친 뒤에야 뒤늦게 "성공"하는 경합을 재현한다. generation 검사가 없다면 뒤늦은
        // 세션이 currentSession을 덮어써서 활성 세션이 누수되고 동일 종목을 두 세션이 동시에 구독하게 된다.
        stubSuccessfulCredentials();
        WebSocketSession staleSession = openSession();
        WebSocketSession activeSession = openSession();
        given(webSocketClient.execute(any(WebSocketHandler.class), anyString()))
                .willReturn(new CompletableFuture<>()) // 1차 시도: 절대 완료되지 않는 핸드셰이크(네트워크 지연 재현)
                .willReturn(CompletableFuture.completedFuture(activeSession)); // 2차 시도(타임아웃 후 재연결): 정상 성공
        given(reconnectPolicy.nextDelay(0)).willReturn(Duration.ofMillis(10));

        KisWebSocketClient client = client(List.of("005930"), Duration.ofMillis(30));
        client.connect(); // 1차 시도(생성=1)

        // 1차 시도가 handshakeTimeout(30ms)을 넘겨 실패로 처리되고 재연결이 예약될 때까지 기다린다.
        verify(reconnectScheduler, timeout(2000)).schedule(any(Runnable.class), eq(10L), eq(TimeUnit.MILLISECONDS));

        // 실제로는 reconnectScheduler가 예약된 delay 이후 connect()를 실행하지만, 여기서는 그 "다음 시도"를
        // 직접 시뮬레이션한다.
        client.connect(); // 2차 시도(생성=2)

        ArgumentCaptor<WebSocketHandler> captor = ArgumentCaptor.forClass(WebSocketHandler.class);
        verify(webSocketClient, times(2)).execute(captor.capture(), anyString());
        WebSocketHandler staleHandler = captor.getAllValues().get(0);
        WebSocketHandler activeHandler = captor.getAllValues().get(1);

        // 2차 시도(활성)가 먼저 정상적으로 연결·재구독을 마친다.
        activeHandler.afterConnectionEstablished(activeSession);

        // 그 후에야 1차 시도(생성=1, 이미 폐기됨)의 원래 핸드셰이크가 뒤늦게 "성공"한다.
        staleHandler.afterConnectionEstablished(staleSession);

        verify(staleSession).close(CloseStatus.NORMAL);
        verify(activeSession, never()).close(any(CloseStatus.class));
        verify(activeSession).sendMessage(any(TextMessage.class));

        // 뒤늦게 닫힌 stale 세션에 대한 종료 통지가 활성 세션과 무관하게 중복 재연결을 예약하지 않는지도 확인한다.
        // scheduleReconnect()가 다시 실행됐다면 reconnectPolicy.nextDelay(1)이 호출됐을 것이다.
        staleHandler.afterConnectionClosed(staleSession, CloseStatus.NORMAL);
        verify(reconnectPolicy, never()).nextDelay(1);
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
    void afterConnectionEstablishedClosesSessionWhenShutdownRunsWhileRegisteringSession() throws Exception {
        stubSuccessfulCredentials();
        WebSocketSession session = openSession();
        given(webSocketClient.execute(any(WebSocketHandler.class), anyString()))
                .willReturn(CompletableFuture.completedFuture(session));

        KisWebSocketClient client = client(List.of());
        client.connect();
        WebSocketHandler handler = capturedHandler();

        // afterConnectionEstablished가 세션을 currentSession에 등록하기 직전(로그의 session.getId() 평가
        // 시점)에 마침 shutdown()이 호출되는 경합을 재현한다. shutdown()의 getAndSet(null)은 아직 등록되지
        // 않은 이 세션을 보지 못하므로, 등록 이후 재검사가 없으면 이 세션은 아무도 닫지 못한 채 leak된다.
        given(session.getId()).willAnswer(invocation -> {
            client.shutdown();
            return "session-1";
        });

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.NORMAL);
        verify(session, never()).sendMessage(any(TextMessage.class));
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

    @Test
    void connectDoesNothingWhenAlreadyShuttingDown() {
        // scheduleReconnect()로 예약해 둔 지연 작업은 shutdown()이 취소하지 않는다. 종료 이후 그 예약이
        // 실행되면 connect()가 shuttingDown을 확인하지 않는 한 종료 도중에도 user-service 호출/핸드셰이크가
        // 새로 시도된다.
        KisWebSocketClient client = client(List.of());
        client.shutdown();

        client.connect();

        verify(userAccountFeignClient, never()).getKisToken(any());
        verify(webSocketClient, never()).execute(any(WebSocketHandler.class), anyString());
    }

    @Test
    void handshakeExceedingConfiguredTimeoutSchedulesReconnect() {
        stubSuccessfulCredentials();
        // execute()가 절대 완료되지 않는 상황(네트워크 문제로 핸드셰이크가 계속 멈춘 경우)을 재현한다.
        // properties.handshakeTimeout()으로 강제 완료되지 않으면 whenComplete가 호출되지 않아
        // scheduleReconnect()도 영원히 호출되지 않는다.
        given(webSocketClient.execute(any(WebSocketHandler.class), anyString()))
                .willReturn(new CompletableFuture<>());
        given(reconnectPolicy.nextDelay(0)).willReturn(Duration.ofMillis(50));

        KisWebSocketClient client = client(List.of(), Duration.ofMillis(30));
        client.connect();

        verify(reconnectScheduler, timeout(2000)).schedule(any(Runnable.class), eq(50L), eq(TimeUnit.MILLISECONDS));
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
        return client(initialTargetStockCodes, Duration.ofSeconds(5));
    }

    private KisWebSocketClient client(List<String> initialTargetStockCodes, Duration handshakeTimeout) {
        MarketWebSocketProperties properties = new MarketWebSocketProperties(
                true, "ws://localhost:31000", SYSTEM_USER_ID, Duration.ofMillis(10), Duration.ofSeconds(1), 2.0,
                List.of(), handshakeTimeout);
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
