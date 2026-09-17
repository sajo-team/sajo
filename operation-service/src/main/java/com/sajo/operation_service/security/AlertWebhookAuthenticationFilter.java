package com.sajo.operation_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

// Alertmanager 외에는 이 경로를 호출할 이유가 없는데, 같은 docker 네트워크 안에서는 어떤 컨테이너든
// (혹은 침투한 공격자든) POST /api/v1/operations/webhook을 직접 호출해서 Prometheus 조회 + OpenAI
// 호출을 반복 유발(비용 남용)하거나, alert 페이로드의 annotations를 통해 LLM 프롬프트 인젝션을 시도할
// 수 있다. libs/common의 InternalApiAuthenticationFilter와 목적은 같지만, Alertmanager가 보낼 수 있는
// 형식(Authorization: Bearer)과 대상 경로가 달라 이 서비스 전용으로 별도로 둔다.
//
// sajo.webhook.secret-file-path가 비어있거나 파일을 못 읽으면 기본적으로 요청을 거부한다(fail-closed).
@Slf4j
public class AlertWebhookAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final String expectedSecret;

    public AlertWebhookAuthenticationFilter(String expectedSecret) {
        this.expectedSecret = expectedSecret;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (expectedSecret == null || expectedSecret.isBlank()) {
            log.error(
                    "sajo.webhook.secret-file-path가 설정되지 않아 모든 alert webhook 호출을 차단합니다. uri={}",
                    request.getRequestURI()
            );
            writeUnauthorized(response);
            return;
        }

        String providedSecret = extractBearerToken(request.getHeader("Authorization"));

        if (providedSecret == null || !constantTimeEquals(expectedSecret, providedSecret)) {
            log.warn(
                    "alert webhook 인증 실패 - Authorization 헤더 누락 또는 불일치. uri={}, remoteAddr={}",
                    request.getRequestURI(), request.getRemoteAddr()
            );
            writeUnauthorized(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorizationHeader.substring(BEARER_PREFIX.length());
    }

    // 문자열 길이/내용에 따라 비교 시간이 달라지는 timing attack을 막기 위해 MessageDigest.isEqual 사용
    private boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"alert webhook 인증에 실패했습니다.\"}");
    }
}
