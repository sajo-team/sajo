package com.sajo.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.common.code.ErrorResponseCode;
import com.sajo.common.jwt.JwtClaims;
import com.sajo.common.jwt.JwtTokenProvider;
import com.sajo.common.jwt.JwtValidationException;
import com.sajo.common.response.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// 요청마다 JWT 검증 후 X-User-Id/X-User-Role 헤더 주입. 서블릿 기반 Gateway라 일반 Filter로 처리.
// downstream은 이 필터가 세팅한 X-User-Id/X-User-Role만 신뢰해야 한다.
// 401 응답은 GlobalExceptionHandler를 안 타므로(필터가 더 앞단) 여기서 직접 JSON 작성
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    // 로그인 없이 접근 가능한 (method, path) 목록. 새 public API는 여기 명시적으로 추가할 것
    private static final List<PublicEndpoint> PERMIT_ALL_ENDPOINTS = List.of(
            new PublicEndpoint("POST", "/api/v1/auth/login"),
            // access token이 만료된 상태에서 호출되는 게 정상 흐름이라 permitAll이어야 한다 -
            // 리뷰 반영: 이게 빠져있으면 Gateway가 만료된 토큰을 보고 먼저 401을 반환해서
            // refresh 자체가 영영 호출될 수 없다. 반대로 /logout은 인증된 사용자만 호출해야
            // 하므로 여기 넣지 않는다.
            new PublicEndpoint("POST", "/api/v1/auth/refresh"),
            new PublicEndpoint("POST", "/api/v1/users"),
            // 와일드카드(/actuator/**) 대신 명시적으로 나열 - 나중에 management.endpoints.web.exposure.include에
            // 다른 엔드포인트(env, heapdump 등)가 추가돼도 이 필터 코드를 안 건드리면 자동으로 열리지 않도록
            new PublicEndpoint("GET", "/actuator/health"),
            new PublicEndpoint("GET", "/actuator/prometheus")
    );

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (isPermitAll(request)) {
            // permitAll 경로도 X-User-Id/X-User-Role/X-Session-Id는 항상 제거 (스푸핑 방지)
            filterChain.doFilter(new UserIdHeaderRequestWrapper(request, null, null, null), response);
            return;
        }

        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Authorization 헤더 없음/형식 오류: uri={}", request.getRequestURI());
            writeUnauthorized(response);
            return;
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length());
        JwtClaims claims;
        try {
            claims = jwtTokenProvider.validateAndGetClaims(token);
        } catch (JwtValidationException e) {
            log.warn("JWT 검증 실패: uri={}, reason={}", request.getRequestURI(), e.getMessage());
            writeUnauthorized(response);
            return;
        }

        filterChain.doFilter(
                new UserIdHeaderRequestWrapper(request, claims.userId().toString(), claims.role(), claims.sessionId()),
                response
        );
    }

    private boolean isPermitAll(HttpServletRequest request) {
        String method = request.getMethod();

        // CORS preflight 요청(OPTIONS이면서 Origin + Access-Control-Request-Method 헤더가
        // 모두 있는 경우)은 브라우저가 Authorization 헤더 없이 보내는 게 정상이라 통과시킨다.
        // CorsFilter(HIGHEST_PRECEDENCE, gateway/config/CorsConfig)가 이 필터보다 먼저 실행돼서
        // 실제 preflight는 이미 앞단에서 처리/차단되지만, 필터 등록 순서 변경 등에 기대지 않도록
        // 이 필터에서도 방어적으로 명시한다.
        //
        // 주의: 단순히 method가 OPTIONS라고 전부 통과시키면 안 된다 - 리뷰 반영. gateway 라우트는
        // Method predicate 없이 Path predicate만 쓰기 때문에, Origin/Access-Control-Request-Method
        // 없는 "일반" OPTIONS 요청까지 permitAll로 처리하면 보호돼야 할 경로가 인증 없이
        // downstream까지 프록시될 수 있다. 그래서 CorsUtils.isPreFlightRequest로 "진짜 preflight"인
        // 경우만 좁혀서 통과시킨다.
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }

        String path = request.getRequestURI();
        return PERMIT_ALL_ENDPOINTS.stream()
                .anyMatch(endpoint -> endpoint.method().equalsIgnoreCase(method)
                        && pathMatcher.match(endpoint.pathPattern(), path));
    }

    // 이 필터는 GlobalExceptionHandler를 거치지 않는 위치라, 공통 스키마(ErrorResponse)를
    // 직접 Jackson으로 직렬화한다 - 문자열을 손으로 조립하면 스키마가 바뀔 때 여기만 따로
    // 맞춰줘야 하는 유지보수 포인트가 생긴다
    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorResponseCode.UNAUTHORIZED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse errorResponse = new ErrorResponse(
                false,
                ErrorResponseCode.UNAUTHORIZED.getErrorCode(),
                ErrorResponseCode.UNAUTHORIZED.getMessage(),
                null
        );
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

    private record PublicEndpoint(String method, String pathPattern) {
    }
}