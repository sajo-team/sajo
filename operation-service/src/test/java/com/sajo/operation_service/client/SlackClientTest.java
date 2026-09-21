package com.sajo.operation_service.client;

import com.sajo.operation_service.client.dto.request.SlackMessageRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// RestClient의 fluent 체인이 uri(String, Object...)처럼 vararg/제네릭 오버로드가 섞여 있어
// RETURNS_DEEP_STUBS로 체인 전체를 한 번에 엮으면 특정 인자 stubbing이 엉뚱한 오버로드로
// 해석될 수 있다(실측 확인) - 각 단계를 명시적으로 mock해서 이 모호성을 피한다.
class SlackClientTest {

    private static final String WEBHOOK_URL = "http://slack.example/webhook";

    private RestClient.ResponseSpec responseSpec;
    private SimpleMeterRegistry meterRegistry;
    private SlackClient slackClient;

    @BeforeEach
    void setup() {
        RestClient restClient = mock(RestClient.class);
        RestClient.Builder restClientBuilder = mock(RestClient.Builder.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClientBuilder.clone()).thenReturn(restClientBuilder);
        when(restClientBuilder.requestFactory(any())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        // body()는 body(Object)/body(StreamingHttpOutputMessage.Body) 오버로드가 같이 있어서
        // any()만 쓰면 더 구체적인 후자로 해석돼 실제 코드(body(Object) 호출)와 다른 메서드가
        // 스텁된다(실측 확인) - any(Object.class)로 타입을 명시해서 오버로드를 고정한다.
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);

        meterRegistry = new SimpleMeterRegistry();

        slackClient = new SlackClient(restClientBuilder, meterRegistry, WEBHOOK_URL, 2000, 5000);
    }

    @Test
    @DisplayName("정상 응답이면 예외 없이 끝나고 실패 카운터가 증가하지 않는다")
    void send_success_doesNotIncrementFailureCounter() {
        slackClient.send(SlackMessageRequest.of("danger", "test"));

        assertThat(meterRegistry.get("slack_send_failures_total").counter().count()).isZero();
    }

    @Test
    @DisplayName("Slack이 4xx/5xx로 응답해도 예외를 던지지 않고 실패 카운터를 증가시킨다")
    void send_httpErrorResponse_swallowsAndIncrementsCounter() {
        HttpServerErrorException exception = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                HttpHeaders.EMPTY,
                "boom".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        when(responseSpec.toBodilessEntity()).thenThrow(exception);

        slackClient.send(SlackMessageRequest.of("danger", "test"));

        assertThat(meterRegistry.get("slack_send_failures_total").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("연결 자체가 안 돼도(ResourceAccessException) 예외를 던지지 않고 실패 카운터를 증가시킨다")
    void send_connectionFailure_swallowsAndIncrementsCounter() {
        when(responseSpec.toBodilessEntity()).thenThrow(new ResourceAccessException("Connection refused"));

        slackClient.send(SlackMessageRequest.of("danger", "test"));

        assertThat(meterRegistry.get("slack_send_failures_total").counter().count()).isEqualTo(1.0);
    }
}
