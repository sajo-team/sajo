package com.other.security;

import com.other.TestApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

// test/resources/application.yaml의 sajo.internal.api-secret=test-internal-api-secret 기준으로 검증한다.
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("InternalApiAuthenticationFilter 통합 테스트")
class InternalApiAuthenticationFilterTest {

    private static final String CORRECT_SECRET = "test-internal-api-secret";

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    @DisplayName("올바른 시크릿 헤더가 있으면 /internal/** 접근이 허용된다")
    void correctSecretIsAllowed() throws Exception {
        HttpResponse<String> response = send(CORRECT_SECRET);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("ok");
    }

    @Test
    @DisplayName("시크릿 헤더가 없으면 401이 반환된다")
    void missingSecretIsUnauthorized() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/internal/v1/test"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("시크릿 헤더 값이 틀리면 401이 반환된다")
    void wrongSecretIsUnauthorized() throws Exception {
        HttpResponse<String> response = send("완전히-틀린-값");

        assertThat(response.statusCode()).isEqualTo(401);
    }

    // 설정값 자체가 비어있는 경우(fail-closed)는 이 클래스의 ApplicationContext와는 다른
    // 프로퍼티 조합이 필요해서 별도 순수 단위 테스트(InternalApiAuthenticationFilterUnitTest)로 분리했다.

    private HttpResponse<String> send(String secret) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/internal/v1/test"))
                .header("X-Internal-Secret", secret)
                .GET()
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
