package com.sajo.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

// /internal/v1/** 는 서비스 간 호출 전용 API라 Gateway를 거치지 않는다(HeaderAuthenticationFilterTest 참고).
// Gateway는 /internal/v1/** 라우팅 규칙 자체가 없어 외부에서는 도달 불가하지만, 같은 docker 네트워크
// 안에서는 어떤 서비스든(혹은 그 네트워크에 침투한 공격자든) 직접 호출이 가능했다 - 예: 임의의 userId로
// POST /internal/v1/accounts/{userId}/token 호출 시 실제 KIS 토큰이 그대로 반환됨.
//
// 이 필터는 그 호출이 "우리 서비스들끼리" 주고받은 게 맞는지 최소한으로 검증한다. Spring Security의
// permitAll() 체인보다 앞단에서 동작해야 하므로 SecurityFilterChain에 넣지 않고 순수 서블릿 Filter로
// FilterRegistrationBean에 등록해 /internal/v1/*에만 적용한다 (CommonInternalApiAutoConfiguration 참고).
//
// sajo.internal.api-secret이 비어있으면 기본적으로 요청을 거부한다(fail-closed) - 값을 깜빡 안 채워서
// "인증이 조용히 꺼진 채로 운영되는" 상황을 막기 위함이다. 로컬/운영 모두 이 값을 반드시 설정해야 한다.
@Slf4j
public class InternalApiAuthenticationFilter extends OncePerRequestFilter {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final String expectedSecret;

    public InternalApiAuthenticationFilter(String expectedSecret) {
        this.expectedSecret = expectedSecret;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        if (expectedSecret == null || expectedSecret.isBlank()) {
            log.error(
                    "sajo.internal.api-secret이 설정되지 않아 모든 internal API 호출을 차단합니다. uri={}",
                    request.getRequestURI()
            );
            writeUnauthorized(response);
            return;
        }

        String providedSecret = request.getHeader(INTERNAL_SECRET_HEADER);

        if (providedSecret == null || !constantTimeEquals(expectedSecret, providedSecret)) {
            log.warn(
                    "internal API 인증 실패 - 시크릿 헤더 누락 또는 불일치. uri={}, remoteAddr={}",
                    request.getRequestURI(), request.getRemoteAddr()
            );
            writeUnauthorized(response);
            return;
        }

        filterChain.doFilter(request, response);
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
        response.getWriter().write("{\"success\":false,\"message\":\"internal API 인증에 실패했습니다.\"}");
    }
}
