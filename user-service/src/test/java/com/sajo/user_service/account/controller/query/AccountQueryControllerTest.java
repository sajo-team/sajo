package com.sajo.user_service.account.controller.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.user_service.account.controller.dto.response.AccountDepositResponse;
import com.sajo.user_service.account.exception.AccountErrorCode;
import com.sajo.user_service.account.service.query.AccountKisQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountQueryController.class)
@Import(GlobalExceptionHandler.class)
class AccountQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountKisQueryService accountKisQueryService;

    @Test
    @DisplayName("예수금 조회에 성공하면 200과 예수금 정보를 반환한다")
    void getDeposit() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        AccountDepositResponse response = new AccountDepositResponse(
                1_000_000L, 900_000L, 800_000L, 1_500_000L, 1_400_000L, 50_000L,
                Instant.parse("2026-01-01T00:00:00Z"));
        given(accountKisQueryService.getDeposit(userId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/accounts/me/deposit").param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.depositTotal").value(1_000_000))
                .andExpect(jsonPath("$.data.d1Deposit").value(900_000))
                .andExpect(jsonPath("$.data.d2Deposit").value(800_000))
                .andExpect(jsonPath("$.data.totalEvaluationAmount").value(1_500_000))
                .andExpect(jsonPath("$.data.netAssetAmount").value(1_400_000))
                .andExpect(jsonPath("$.data.totalProfitLoss").value(50_000));
    }

    @Test
    @DisplayName("계좌가 없으면 예수금 조회는 404를 반환한다")
    void getDepositAccountNotFound() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        given(accountKisQueryService.getDeposit(userId))
                .willThrow(new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/accounts/me/deposit").param("userId", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_0006"));
    }

    @Test
    @DisplayName("KIS 잔고조회에 실패하면 예수금 조회는 502를 반환한다")
    void getDepositKisBalanceInquiryFailed() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        given(accountKisQueryService.getDeposit(userId))
                .willThrow(new BusinessException(AccountErrorCode.KIS_BALANCE_INQUIRY_FAILED));

        // when & then
        mockMvc.perform(get("/api/v1/accounts/me/deposit").param("userId", userId.toString()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_0009"));
    }
}
