package com.other.security;

import com.sajo.common.security.InternalApiAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("InternalApiAuthenticationFilter 단위 테스트")
class InternalApiAuthenticationFilterUnitTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("expectedSecret이 설정되지 않았으면(null/빈 문자열/공백) 요청을 거부한다 - fail-closed")
    void rejectsWhenSecretNotConfigured(String blankSecret) throws Exception {
        InternalApiAuthenticationFilter filter = new InternalApiAuthenticationFilter(blankSecret);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Internal-Secret")).thenReturn("아무값이나-넣어도-거부돼야함");
        when(request.getRequestURI()).thenReturn("/internal/v1/accounts/00000000-0000-0000-0000-000000000000/token");

        HttpServletResponse response = mockResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("헤더 값이 설정된 시크릿과 정확히 일치하면 다음 필터로 넘어간다")
    void passesWhenSecretMatches() throws Exception {
        InternalApiAuthenticationFilter filter = new InternalApiAuthenticationFilter("correct-secret");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Internal-Secret")).thenReturn("correct-secret");

        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(eq(request), eq(response));
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("헤더 값이 설정된 시크릿과 다르면 401을 반환하고 다음 필터로 넘기지 않는다")
    void rejectsWhenSecretDoesNotMatch() throws Exception {
        InternalApiAuthenticationFilter filter = new InternalApiAuthenticationFilter("correct-secret");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Internal-Secret")).thenReturn("wrong-secret");
        when(request.getRequestURI()).thenReturn("/internal/v1/trading/users/00000000-0000-0000-0000-000000000000/active-status");
        when(request.getRemoteAddr()).thenReturn("172.18.0.5");

        HttpServletResponse response = mockResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("헤더 자체가 없으면(null) 401을 반환한다")
    void rejectsWhenHeaderMissing() throws Exception {
        InternalApiAuthenticationFilter filter = new InternalApiAuthenticationFilter("correct-secret");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Internal-Secret")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/internal/v1/users/00000000-0000-0000-0000-000000000000");
        when(request.getRemoteAddr()).thenReturn("172.18.0.6");

        HttpServletResponse response = mockResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(Mockito.any(), Mockito.any());
    }

    // setStatus(401) 이후 response.getWriter()까지 호출되므로, mock에 StringWriter 기반
    // PrintWriter를 연결해줘야 NPE 없이 필터가 끝까지 실행된다.
    private HttpServletResponse mockResponse() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));
        return response;
    }
}
