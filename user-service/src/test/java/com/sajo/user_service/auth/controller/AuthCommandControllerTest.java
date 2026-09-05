package com.sajo.user_service.auth.controller;

import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.user_service.auth.service.command.AuthCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthCommandController.class)
@Import(GlobalExceptionHandler.class)
class AuthCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthCommandService authCommandService;

    @Test
    @DisplayName("로그아웃에 성공하면 200을 반환한다")
    void logoutSucceeds() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        // when & then
        mockMvc.perform(post("/api/v1/auth/logout").header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authCommandService).logout(userId);
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 로그아웃은 400을 반환한다")
    void logoutFailsWithoutUserHeader() throws Exception {
        // when & then - Gateway를 거치지 않은 요청 (MissingRequestHeaderException -> 400)
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isBadRequest());
    }
}
