package com.sajo.user_service.auth.controller.internal;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.user_service.auth.controller.dto.response.UserStatusResponse;
import com.sajo.user_service.auth.exception.UserErrorCode;
import com.sajo.user_service.auth.service.query.UserInternalQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthInternalController.class)
@Import(GlobalExceptionHandler.class)
class AuthInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserInternalQueryService userInternalQueryService;

    @Test
    @DisplayName("존재하는 사용자를 조회하면 200과 상태를 반환한다")
    void getUserStatusSucceeds() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        given(userInternalQueryService.getUserStatus(userId))
                .willReturn(new UserStatusResponse(userId, UserStatusResponse.UserStatus.ACTIVE));

        // when & then
        mockMvc.perform(get("/internal/v1/users/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("존재하지 않는 사용자를 조회하면 404를 반환한다")
    void getUserStatusReturns404WhenNotFound() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        willThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND))
                .given(userInternalQueryService).getUserStatus(userId);

        // when & then
        mockMvc.perform(get("/internal/v1/users/{userId}", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_0005"));
    }
}
