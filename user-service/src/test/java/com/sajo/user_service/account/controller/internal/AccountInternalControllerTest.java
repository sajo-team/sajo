package com.sajo.user_service.account.controller.internal;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.user_service.account.controller.dto.response.AccessTokenResponse;
import com.sajo.user_service.account.controller.dto.response.AccountOrderInfoResponse;
import com.sajo.user_service.account.controller.dto.response.ApprovalKeyResponse;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.exception.AccountErrorCode;
import com.sajo.user_service.account.service.query.AccountKisQueryService;
import com.sajo.user_service.account.service.query.AccountQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountInternalController.class)
@Import(GlobalExceptionHandler.class)
class AccountInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountKisQueryService accountKisQueryService;

    @MockitoBean
    private AccountQueryService accountQueryService;

    @Test
    @DisplayName("KIS 접근토큰 발급에 성공하면 200과 토큰 정보를 반환한다")
    void getToken() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        given(accountKisQueryService.getKisAccessToken(userId))
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
        given(accountKisQueryService.getKisAccessToken(userId))
                .willThrow(new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        // when & then
        mockMvc.perform(post("/internal/v1/accounts/{userId}/token", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_0006"));
    }

    @Test
    @DisplayName("KIS 접속키 발급에 성공하면 200과 접속키를 반환한다")
    void getWsToken() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        given(accountKisQueryService.getKisApprovalKey(userId))
                .willReturn(new ApprovalKeyResponse("issued-approval-key"));

        // when & then
        mockMvc.perform(post("/internal/v1/accounts/{userId}/ws-token", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalKey").value("issued-approval-key"));
    }

    @Test
    @DisplayName("계좌가 없으면 접속키 발급도 404를 반환한다")
    void getWsTokenAccountNotFound() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        given(accountKisQueryService.getKisApprovalKey(userId))
                .willThrow(new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        // when & then
        mockMvc.perform(post("/internal/v1/accounts/{userId}/ws-token", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_0006"));
    }

    @Test
    @DisplayName("주문용 계좌 정보 조회에 성공하면 200과 cano/accountProductCode/accountType을 반환한다")
    void getAccountOrderInfo() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        given(accountQueryService.getAccountOrderInfo(userId))
                .willReturn(new AccountOrderInfoResponse("12345678", "01", AccountType.REAL.name()));

        // when & then
        mockMvc.perform(get("/internal/v1/accounts/{userId}/order-info", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cano").value("12345678"))
                .andExpect(jsonPath("$.accountProductCode").value("01"))
                .andExpect(jsonPath("$.accountType").value("REAL"));
    }

    @Test
    @DisplayName("계좌가 없으면 주문용 계좌 정보 조회도 404를 반환한다")
    void getAccountOrderInfoAccountNotFound() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        given(accountQueryService.getAccountOrderInfo(userId))
                .willThrow(new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/internal/v1/accounts/{userId}/order-info", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_0006"));
    }

    @Test
    @DisplayName("저장된 accountNo 형식이 깨져있으면 주문용 계좌 정보 조회는 500과 INVALID_ACCOUNT_NO_FORMAT을 반환한다")
    void getAccountOrderInfoFailsWhenPersistedAccountNoHasInvalidFormat() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        given(accountQueryService.getAccountOrderInfo(userId))
                .willThrow(new BusinessException(AccountErrorCode.INVALID_ACCOUNT_NO_FORMAT));

        // when & then
        mockMvc.perform(get("/internal/v1/accounts/{userId}/order-info", userId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_0008"));
    }
}
