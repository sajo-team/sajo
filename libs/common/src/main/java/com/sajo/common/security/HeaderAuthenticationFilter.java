package com.sajo.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // Gateway가 JWT 검증 후 전달한 내부 헤더를 가져온다.
        String userIdHeader = request.getHeader(USER_ID_HEADER);
        String role = request.getHeader(USER_ROLE_HEADER);

        if (userIdHeader != null && !userIdHeader.isBlank()
                && role != null && !role.isBlank()) {

            // 이 필터는 DispatcherServlet 이전 단계라 GlobalExceptionHandler가 예외를 못 잡는다.
            // X-User-Id가 UUID 형식이 아니면(게이트웨이 우회/오설정 등) 익명으로 처리 - 헤더가
            // 아예 없는 경우와 동일하게 방어적으로 넘어간다.
            try {
                UUID userId = UUID.fromString(userIdHeader);
                UsernamePasswordAuthenticationToken authentication = getUsernamePasswordAuthenticationToken(role, userId);

                // 생성한 Authentication을 SecurityContext에 저장한다.
                // 이후 @PreAuthorize가 이 Authentication의 Authority를 이용해 권한을 검사하게 된다.
                SecurityContextHolder.getContext()
                        .setAuthentication(authentication);
            } catch (IllegalArgumentException e) {
                log.warn("X-User-Id 헤더가 유효한 UUID 형식이 아님: {}", userIdHeader);
            }
        }

        // 인증 정보를 설정한 뒤 다음 Security Filter 또는 Controller로 요청을 전달한다.
        filterChain.doFilter(request, response);
    }

    private static UsernamePasswordAuthenticationToken getUsernamePasswordAuthenticationToken(String role, UUID userId) {
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);

        // principal에는 사용자 ID를 저장한다.
        // 이후 Controller나 Service에서 authentication.getName()을 호출하면 userId를 얻을 수 있다.
        // credentials는 이미 Gateway에서 인증이 끝났으므로 null로 둔다.
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(authority)
        );
    }
}
