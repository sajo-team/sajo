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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("HeaderAuthenticationFilter + 보안 체인 통합 테스트")
class HeaderAuthenticationFilterTest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    @DisplayName("올바른 role이면 접근이 허용된다")
    void correctRoleIsAllowed() throws Exception {
        HttpResponse<String> response = send(UUID.randomUUID().toString(), "MASTER");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("ok");
    }

    @Test
    @DisplayName("role이 맞지 않으면 403이 반환된다")
    void wrongRoleIsForbidden() throws Exception {
        HttpResponse<String> response = send(UUID.randomUUID().toString(), "GUEST");

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("인증 헤더가 없으면 403이 반환된다")
    void missingHeadersIsForbidden() throws Exception {
        // 헤더가 없으면 Spring Security의 익명 인증(ROLE_ANONYMOUS)이 적용되고,
        // hasRole('MASTER')를 만족 못 해 AccessDeniedException -> 403이 되는 게 맞는 동작이다.
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/secured"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("/internal 경로는 헤더 없이도 열려있다")
    void internalPathIsOpenWithoutAnyHeaders() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/internal/test"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("ok");
    }

    private HttpResponse<String> send(String userId, String role) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/secured"))
                .header("X-User-Id", userId)
                .header("X-User-Role", role)
                .GET()
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
