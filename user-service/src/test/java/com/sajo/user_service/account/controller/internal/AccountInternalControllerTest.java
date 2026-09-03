package com.sajo.user_service.account.controller.internal;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.user_service.account.controller.dto.response.AccessTokenResponse;
import com.sajo.user_service.account.exception.AccountErrorCode;
import com.sajo.user_service.account.service.query.AccountKisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountInternalController.class)
@Import(GlobalExceptionHandler.class)
class AccountInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountKisService accountKisService;

    @Test
    @DisplayName("KIS 접근토큰 발급에 성공하면 200과 토큰 정보를 반환한다")
    void getToken() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        given(accountKisService.getKisAccessToken(userId))
                .willReturn(new AccessTokenResponse("issued-token", "app-key", "secret-key"));

        // when & then
        mockMvc.perform(post("/internal/v1/accounts/{userId}/token", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("issued-token"))
                .andExpect(jsonPath("$.appKey").value("app-key"))
                .andExpect(jsonPath("$.secretKey").value("secret-key"));
    }

    @Test
    @DisplayName("계좌가 없으면 404를 반환한다")
    void getTokenAccountNotFound() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        given(accountKisService.getKisAccessToken(userId))
                .willThrow(new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        // when & then
        mockMvc.perform(post("/internal/v1/accounts/{userId}/token", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_0006"));
    }
}
