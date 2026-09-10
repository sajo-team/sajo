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
    @DisplayName("X-User-Id가 유효한 UUID 형식이 아니면 익명으로 처리되어 403이 반환된다")
    void malformedUserIdIsTreatedAsAnonymous() throws Exception {
        // 게이트웨이를 정상적으로 거치면 발생하지 않지만, 우회 호출/오설정 대비 방어 확인
        HttpResponse<String> response = send("not-a-valid-uuid", "MASTER");

        assertThat(response.statusCode()).isEqualTo(403);
    }

    // /internal 경로의 인증 여부는 이제 InternalApiAuthenticationFilter의 책임이다.
    // 관련 테스트는 InternalApiAuthenticationFilterTest 참고 - 예전에는 이 경로가
    // 헤더 없이도 열려있는 게 "정상"이었지만, 그게 바로 고쳐야 했던 문제였다.

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
