package com.sajo.user_service.auth.controller;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.user_service.auth.domain.User;
import com.sajo.user_service.auth.exception.UserErrorCode;
import com.sajo.user_service.auth.service.query.UserQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserQueryController.class)
@Import(GlobalExceptionHandler.class)
class UserQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserQueryService userQueryService;

    @Test
    @DisplayName("내 정보 조회에 성공하면 200과 id/email/name/role을 반환한다")
    void getMyInfoSucceeds() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        User user = User.of("test@sajo.com", "encoded-password", "테스트");
        ReflectionTestUtils.setField(user, "id", userId);
        given(userQueryService.getMyInfo(userId)).willReturn(user);

        // when & then
        mockMvc.perform(get("/api/v1/users/me").header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.email").value("test@sajo.com"))
                .andExpect(jsonPath("$.data.name").value("테스트"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 내 정보 조회는 404를 반환한다")
    void getMyInfoNotFound() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        given(userQueryService.getMyInfo(userId))
                .willThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/users/me").header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_0005"));
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 내 정보 조회는 400을 반환한다")
    void getMyInfoWithoutUserHeader() throws Exception {
        // when & then - Gateway를 거치지 않은 요청 (MissingRequestHeaderException -> 400)
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isBadRequest());
    }
}
