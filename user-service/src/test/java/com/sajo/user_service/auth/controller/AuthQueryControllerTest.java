package com.sajo.user_service.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.user_service.auth.controller.dto.request.LoginRequest;
import com.sajo.user_service.auth.controller.dto.response.LoginResponse;
import com.sajo.user_service.auth.exception.UserErrorCode;
import com.sajo.user_service.auth.service.query.AuthQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthQueryController.class)
@Import(GlobalExceptionHandler.class)
class AuthQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthQueryService authQueryService;

    @Test
    @DisplayName("로그인에 성공하면 200과 access token을 반환한다")
    void loginSucceeds() throws Exception {
        // given
        LoginRequest request = new LoginRequest("test@sajo.com", "password1");
        given(authQueryService.login(request)).willReturn(LoginResponse.of("issued-token", 3600L));

        // when & then
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("issued-token"))
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
                .given(authQueryService).login(request);

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
}
