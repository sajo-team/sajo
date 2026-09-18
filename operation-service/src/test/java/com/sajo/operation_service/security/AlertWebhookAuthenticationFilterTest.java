package com.sajo.operation_service.security;

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

@DisplayName("AlertWebhookAuthenticationFilter 단위 테스트")
class AlertWebhookAuthenticationFilterTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("expectedSecret이 설정되지 않았으면(null/빈 문자열/공백) 요청을 거부한다 - fail-closed")
    void rejectsWhenSecretNotConfigured(String blankSecret) throws Exception {
        AlertWebhookAuthenticationFilter filter = new AlertWebhookAuthenticationFilter(blankSecret);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer 아무값이나-넣어도-거부돼야함");
        when(request.getRequestURI()).thenReturn("/api/v1/operations/webhook");

        HttpServletResponse response = mockResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("Authorization: Bearer 헤더 값이 설정된 시크릿과 정확히 일치하면 다음 필터로 넘어간다")
    void passesWhenSecretMatches() throws Exception {
        AlertWebhookAuthenticationFilter filter = new AlertWebhookAuthenticationFilter("correct-secret");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer correct-secret");

        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(eq(request), eq(response));
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("Bearer 토큰 값이 다르면 401을 반환하고 다음 필터로 넘기지 않는다")
    void rejectsWhenSecretDoesNotMatch() throws Exception {
        AlertWebhookAuthenticationFilter filter = new AlertWebhookAuthenticationFilter("correct-secret");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer wrong-secret");
        when(request.getRequestURI()).thenReturn("/api/v1/operations/webhook");
        when(request.getRemoteAddr()).thenReturn("172.18.0.5");

        HttpServletResponse response = mockResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("Authorization 헤더 자체가 없으면(null) 401을 반환한다")
    void rejectsWhenHeaderMissing() throws Exception {
        AlertWebhookAuthenticationFilter filter = new AlertWebhookAuthenticationFilter("correct-secret");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/v1/operations/webhook");
        when(request.getRemoteAddr()).thenReturn("172.18.0.6");

        HttpServletResponse response = mockResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("Bearer 접두사 없이 값만 온 경우(Basic 등 다른 스킴 포함) 401을 반환한다")
    void rejectsWhenBearerPrefixMissing() throws Exception {
        AlertWebhookAuthenticationFilter filter = new AlertWebhookAuthenticationFilter("correct-secret");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("correct-secret");
        when(request.getRequestURI()).thenReturn("/api/v1/operations/webhook");
        when(request.getRemoteAddr()).thenReturn("172.18.0.7");

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
