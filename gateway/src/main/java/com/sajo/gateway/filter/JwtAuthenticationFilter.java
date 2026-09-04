package com.sajo.gateway.filter;
 
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.common.code.ErrorResponseCode;
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
import org.springframework.web.filter.OncePerRequestFilter;
 
import java.io.IOException;
import java.util.List;
import java.util.UUID;
 
// 요청마다 JWT 검증 후 X-User-Id 헤더 주입. 서블릿 기반 Gateway라 일반 Filter로 처리.
// downstream은 이 필터가 세팅한 X-User-Id만 신뢰해야 한다.
// 401 응답은 GlobalExceptionHandler를 안 타므로(필터가 더 앞단) 여기서 직접 JSON 작성
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
 
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
 
    private static final List<PublicEndpoint> PERMIT_ALL_ENDPOINTS = List.of(
            new PublicEndpoint("POST", "/api/v1/auth/login"),
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
            // permitAll 경로도 X-User-Id는 항상 제거 (스푸핑 방지)
            filterChain.doFilter(new UserIdHeaderRequestWrapper(request, null), response);
            return;
        }
 
        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Authorization 헤더 없음/형식 오류: uri={}", request.getRequestURI());
            writeUnauthorized(response);
            return;
        }
 
        String token = authorizationHeader.substring(BEARER_PREFIX.length());
        UUID userId;
        try {
            userId = jwtTokenProvider.validateAndGetUserId(token);
        } catch (JwtValidationException e) {
            log.warn("JWT 검증 실패: uri={}, reason={}", request.getRequestURI(), e.getMessage());
            writeUnauthorized(response);
            return;
        }
 
        filterChain.doFilter(new UserIdHeaderRequestWrapper(request, userId.toString()), response);
    }
 
    private boolean isPermitAll(HttpServletRequest request) {
        String method = request.getMethod();
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
