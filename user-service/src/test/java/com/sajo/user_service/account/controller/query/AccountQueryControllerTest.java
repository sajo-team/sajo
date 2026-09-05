package com.sajo.user_service.account.controller.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.user_service.account.controller.dto.response.AccountDepositResponse;
import com.sajo.user_service.account.controller.dto.response.AccountHoldingResponse;
import com.sajo.user_service.account.controller.dto.response.AccountHoldingsResponse;
import com.sajo.user_service.account.exception.AccountErrorCode;
import com.sajo.user_service.account.service.query.AccountKisQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
        mockMvc.perform(get("/api/v1/accounts/me/deposit").header("X-User-Id", userId.toString()))
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
        mockMvc.perform(get("/api/v1/accounts/me/deposit").header("X-User-Id", userId.toString()))
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
        mockMvc.perform(get("/api/v1/accounts/me/deposit").header("X-User-Id", userId.toString()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_0009"));
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 예수금 조회는 401을 반환한다")
    void getDepositWithoutUserHeader() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/accounts/me/deposit"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("보유종목 조회(커서 없음)에 성공하면 200과 보유종목 목록/다음 커서를 반환한다")
    void getHoldings() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        AccountHoldingResponse holding = new AccountHoldingResponse(
                "005930", "삼성전자", 10L, 10L, new BigDecimal("70000.5"),
                75_000L, 750_000L, 49_995L, new BigDecimal("7.14"));
        AccountHoldingsResponse response = new AccountHoldingsResponse(
                List.of(holding), true, "next-fk", "next-nk", Instant.parse("2026-01-01T00:00:00Z"));

        given(accountKisQueryService.getHoldings(eq(userId), isNull(), isNull())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/accounts/me/holdings").header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.holdings[0].stockCode").value("005930"))
                .andExpect(jsonPath("$.data.holdings[0].stockName").value("삼성전자"))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.nextCtxAreaFk100").value("next-fk"))
                .andExpect(jsonPath("$.data.nextCtxAreaNk100").value("next-nk"));
    }

    @Test
    @DisplayName("보유종목 조회(커서 있음)는 요청 파라미터를 그대로 서비스에 전달한다")
    void getHoldingsWithCursor() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        AccountHoldingsResponse response = new AccountHoldingsResponse(
                List.of(), false, null, null, Instant.parse("2026-01-01T00:00:00Z"));

        given(accountKisQueryService.getHoldings(userId, "prev-fk", "prev-nk")).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/accounts/me/holdings")
                        .header("X-User-Id", userId.toString())
                        .param("ctxAreaFk100", "prev-fk")
                        .param("ctxAreaNk100", "prev-nk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("계좌가 없으면 보유종목 조회도 404를 반환한다")
    void getHoldingsAccountNotFound() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        given(accountKisQueryService.getHoldings(eq(userId), isNull(), isNull()))
                .willThrow(new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/accounts/me/holdings").header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_0006"));
    }

    @Test
    @DisplayName("ctxAreaFk100/ctxAreaNk100 중 하나만 오면 보유종목 조회는 400을 반환한다")
    void getHoldingsInvalidCursor() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        given(accountKisQueryService.getHoldings(userId, "only-fk", null))
                .willThrow(new BusinessException(AccountErrorCode.INVALID_CONTINUATION_CURSOR));

        // when & then
        mockMvc.perform(get("/api/v1/accounts/me/holdings")
                        .header("X-User-Id", userId.toString())
                        .param("ctxAreaFk100", "only-fk"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_0010"));
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 보유종목 조회는 401을 반환한다")
    void getHoldingsWithoutUserHeader() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/accounts/me/holdings"))
                .andExpect(status().isUnauthorized());
    }
}
