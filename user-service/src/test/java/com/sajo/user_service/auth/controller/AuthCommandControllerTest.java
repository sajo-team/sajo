package com.sajo.user_service.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.user_service.auth.controller.dto.request.LoginRequest;
import com.sajo.user_service.auth.controller.dto.request.RefreshRequest;
import com.sajo.user_service.auth.controller.dto.response.LoginResponse;
import com.sajo.user_service.auth.exception.UserErrorCode;
import com.sajo.user_service.auth.service.command.AuthCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 리뷰 반영 - login()/refresh()/logout() 전부 Command로 통합됐다 (이유는
// AuthCommandServiceTest 상단 주석 참고).
@WebMvcTest(AuthCommandController.class)
@Import(GlobalExceptionHandler.class)
class AuthCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthCommandService authCommandService;

    @Test
    @DisplayName("로그인에 성공하면 200과 access/refresh token을 반환한다")
    void loginSucceeds() throws Exception {
        // given
        LoginRequest request = new LoginRequest("test@sajo.com", "password1");
        given(authCommandService.login(request))
                .willReturn(LoginResponse.of("issued-access-token", "issued-refresh-token", 3600L));

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("issued-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("issued-refresh-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("이메일 형식이 아니면 400을 반환한다")
    void loginFailsWhenEmailInvalid() throws Exception {
        // given
        LoginRequest request = new LoginRequest("not-an-email", "password1");

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("자격 증명이 틀리면 401을 반환한다")
    void loginFailsWhenCredentialsInvalid() throws Exception {
        // given
        LoginRequest request = new LoginRequest("test@sajo.com", "wrong-password");
        willThrow(new BusinessException(UserErrorCode.INVALID_CREDENTIALS))
                .given(authCommandService).login(request);

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("USER_0002"));
    }

    @Test
    @DisplayName("로그인 시도 횟수를 초과하면 429를 반환한다")
    void loginFailsWhenTooManyAttempts() throws Exception {
        // given
        LoginRequest request = new LoginRequest("locked@sajo.com", "password1");
        willThrow(new BusinessException(UserErrorCode.TOO_MANY_LOGIN_ATTEMPTS))
                .given(authCommandService).login(request);

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("USER_0003"));
    }

    @Test
    @DisplayName("유효한 refresh token이면 200과 새 access/refresh token을 반환한다")
    void refreshSucceeds() throws Exception {
        // given
        RefreshRequest request = new RefreshRequest("old-refresh-token");
        given(authCommandService.refresh(request))
                .willReturn(LoginResponse.of("new-access-token", "new-refresh-token", 3600L));

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));
    }

    @Test
    @DisplayName("refresh token이 비어있으면 400을 반환한다")
    void refreshFailsWhenTokenBlank() throws Exception {
        // given
        RefreshRequest request = new RefreshRequest("");

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // 리뷰 반영 - permitAll(인증 없이 호출 가능)인 엔드포인트라 임의로 긴 문자열을
    // 반복적으로 보내는 요청에 대한 최소한의 방어선으로 길이를 제한했다.
    @Test
    @DisplayName("refresh token이 너무 길면 400을 반환한다")
    void refreshFailsWhenTokenTooLong() throws Exception {
        // given
        RefreshRequest request = new RefreshRequest("a".repeat(513));

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("유효하지 않은 refresh token이면 401을 반환한다")
    void refreshFailsWhenTokenInvalid() throws Exception {
        // given
        RefreshRequest request = new RefreshRequest("bad-token");
        willThrow(new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN))
                .given(authCommandService).refresh(request);

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("USER_0004"));
    }

    @Test
    @DisplayName("로그아웃에 성공하면 200을 반환하고, X-Session-Id로 지목된 세션만 무효화한다")
    void logoutSucceeds() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        String sessionId = "session-abc";

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .header("X-User-Id", userId.toString())
                                .header("X-Session-Id", sessionId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authCommandService).logout(sessionId);
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 로그아웃은 400을 반환한다")
    void logoutFailsWithoutUserHeader() throws Exception {
        // when & then - Gateway를 거치지 않은 요청 (MissingRequestHeaderException -> 400)
        mockMvc.perform(post("/api/v1/auth/logout").header("X-Session-Id", "session-abc"))
                .andExpect(status().isBadRequest());
    }

    // 리뷰 반영 - X-Session-Id가 없어도 400이 아니라 200으로 성공 처리되어야 한다.
    // 로그인 시점에 Redis 장애로 fail-open되어 sessionId 없이 access token이 발급된
    // 세션이 이 경우에 해당하는데, 그런 세션도 로그아웃 자체는 정상적으로(그리고
    // 클라이언트 입장에서 성공한 것처럼) 처리되어야 한다.
    @Test
    @DisplayName("X-Session-Id 헤더가 없어도 로그아웃은 200을 반환한다 (fail-open 로그인 이후 대응)")
    void logoutSucceedsWithoutSessionHeader() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        // when & then
        mockMvc.perform(post("/api/v1/auth/logout").header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authCommandService).logout(null);
    }
}
