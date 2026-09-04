package com.sajo.user_service.account.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.client.KisClient;
import com.sajo.user_service.account.client.KisContinuationResult;
import com.sajo.user_service.account.client.dto.response.KisBalanceHoldingResponse;
import com.sajo.user_service.account.client.dto.response.KisBalanceResponse;
import com.sajo.user_service.account.client.dto.response.KisBalanceSummaryResponse;
import com.sajo.user_service.account.controller.dto.response.AccessTokenResponse;
import com.sajo.user_service.account.controller.dto.response.AccountDepositResponse;
import com.sajo.user_service.account.controller.dto.response.AccountHoldingsResponse;
import com.sajo.user_service.account.controller.dto.response.ApprovalKeyResponse;
import com.sajo.user_service.account.domain.Account;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.exception.AccountErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AccountKisQueryServiceTest {

    @Mock
    private AccountQueryService accountQueryService;

    @Mock
    private KisTokenCacheQueryService kisTokenCacheQueryService;

    @Mock
    private KisClient kisClient;

    private AccountKisQueryService accountKisQueryService;

    @BeforeEach
    void setUp() {
        accountKisQueryService =
                new AccountKisQueryService(accountQueryService, kisTokenCacheQueryService, kisClient);
    }

    @Test
    @DisplayName("예수금 조회에 성공하면 output2 값을 그대로 매핑해서 반환한다")
    void getDeposit() {
        // given
        UUID userId = UUID.randomUUID();
        Account account = Account.createAccount(
                userId, "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);
        KisBalanceResponse kisBalanceResponse = new KisBalanceResponse(
                "0", "MSG_CD", "정상처리 되었습니다", null, null, List.of(), List.of(depositSummary()));

        given(accountQueryService.getAccountByUserId(userId)).willReturn(account);
        given(kisTokenCacheQueryService.getAccessToken(userId, "app-key", "secret-key", AccountType.REAL))
                .willReturn("issued-token");
        given(kisClient.inquireBalance(
                "issued-token", "app-key", "secret-key", "12345678", "01", AccountType.REAL))
                .willReturn(kisBalanceResponse);

        // when
        AccountDepositResponse result = accountKisQueryService.getDeposit(userId);

        // then
        assertThat(result.depositTotal()).isEqualTo(1_000_000L);
        assertThat(result.d1Deposit()).isEqualTo(900_000L);
        assertThat(result.d2Deposit()).isEqualTo(800_000L);
        assertThat(result.totalEvaluationAmount()).isEqualTo(1_500_000L);
        assertThat(result.netAssetAmount()).isEqualTo(1_400_000L);
        assertThat(result.totalProfitLoss()).isEqualTo(50_000L);

        InOrder inOrder = inOrder(accountQueryService, kisTokenCacheQueryService, kisClient);
        inOrder.verify(accountQueryService).getAccountByUserId(userId);
        inOrder.verify(kisTokenCacheQueryService).getAccessToken(userId, "app-key", "secret-key", AccountType.REAL);
        inOrder.verify(kisClient).inquireBalance(
                "issued-token", "app-key", "secret-key", "12345678", "01", AccountType.REAL);
    }

    @Test
    @DisplayName("예수금 조회 시 계좌가 없으면 ACCOUNT_NOT_FOUND 예외를 그대로 전파하고 KIS는 호출하지 않는다")
    void getDepositFailsWhenAccountNotFound() {
        // given
        UUID userId = UUID.randomUUID();
        given(accountQueryService.getAccountByUserId(userId))
                .willThrow(new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> accountKisQueryService.getDeposit(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
                });

        verifyNoInteractions(kisTokenCacheQueryService, kisClient);
    }

    @Test
    @DisplayName("KIS 응답의 output2가 비어 있으면 KIS_BALANCE_INQUIRY_FAILED 예외를 던진다")
    void getDepositFailsWhenOutput2IsEmpty() {
        // given
        UUID userId = UUID.randomUUID();
        Account account = Account.createAccount(
                userId, "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);
        KisBalanceResponse kisBalanceResponse =
                new KisBalanceResponse("0", "MSG_CD", "정상처리 되었습니다", null, null, List.of(), List.of());

        given(accountQueryService.getAccountByUserId(userId)).willReturn(account);
        given(kisTokenCacheQueryService.getAccessToken(userId, "app-key", "secret-key", AccountType.REAL))
                .willReturn("issued-token");
        given(kisClient.inquireBalance(
                "issued-token", "app-key", "secret-key", "12345678", "01", AccountType.REAL))
                .willReturn(kisBalanceResponse);

        // when & then
        assertThatThrownBy(() -> accountKisQueryService.getDeposit(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.KIS_BALANCE_INQUIRY_FAILED);
                });
    }

    private static KisBalanceSummaryResponse depositSummary() {
        return new KisBalanceSummaryResponse(
                "1000000", // dnca_tot_amt
                "900000", // nxdy_excc_amt
                "800000", // prvs_rcdl_excc_amt
                null, // cma_evlu_amt
                null, // bfdy_buy_amt
                null, // thdt_buy_amt
                null, // nxdy_auto_rdpt_amt
                null, // bfdy_sll_amt
                null, // thdt_sll_amt
                null, // d2_auto_rdpt_amt
                null, // bfdy_tlex_amt
                null, // thdt_tlex_amt
                null, // tot_loan_amt
                null, // scts_evlu_amt
                "1500000", // tot_evlu_amt
                "1400000", // nass_amt
                null, // fncg_gld_auto_rdpt_yn
                null, // pchs_amt_smtl_amt
                null, // evlu_amt_smtl_amt
                "50000", // evlu_pfls_smtl_amt
                null, // tot_stln_slng_chgs
                null, // bfdy_tot_asst_evlu_amt
                null, // asst_icdc_amt
                null // asst_icdc_erng_rt
        );
    }

    @Test
    @DisplayName("보유종목 조회(더 있음)에 성공하면 보유종목 목록과 다음 커서를 반환한다")
    void getHoldingsWithNext() {
        // given
        UUID userId = UUID.randomUUID();
        Account account = Account.createAccount(
                userId, "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);
        KisBalanceResponse kisBalanceResponse = new KisBalanceResponse(
                "0", "MSG_CD", "정상처리 되었습니다", "next-fk", "next-nk",
                List.of(holding()), List.of());
        KisContinuationResult<KisBalanceResponse> continuationResult =
                new KisContinuationResult<>(kisBalanceResponse, true);

        given(accountQueryService.getAccountByUserId(userId)).willReturn(account);
        given(kisTokenCacheQueryService.getAccessToken(userId, "app-key", "secret-key", AccountType.REAL))
                .willReturn("issued-token");
        given(kisClient.inquireBalance(
                "issued-token", "app-key", "secret-key", "12345678", "01", AccountType.REAL, null, null))
                .willReturn(continuationResult);

        // when
        AccountHoldingsResponse result = accountKisQueryService.getHoldings(userId, null, null);

        // then
        assertThat(result.holdings()).hasSize(1);
        assertThat(result.holdings().getFirst().stockCode()).isEqualTo("005930");
        assertThat(result.holdings().getFirst().stockName()).isEqualTo("삼성전자");
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCtxAreaFk100()).isEqualTo("next-fk");
        assertThat(result.nextCtxAreaNk100()).isEqualTo("next-nk");
    }

    @Test
    @DisplayName("보유종목 조회(마지막 페이지)에 성공하면 다음 커서는 null로 반환한다")
    void getHoldingsWithoutNext() {
        // given
        UUID userId = UUID.randomUUID();
        Account account = Account.createAccount(
                userId, "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);
        // KIS가 마지막 페이지에도 ctx_area 값을 실어 보낼 수 있지만, hasNext가 false면 무시하고 null로 응답해야 한다
        KisBalanceResponse kisBalanceResponse = new KisBalanceResponse(
                "0", "MSG_CD", "정상처리 되었습니다", "stale-fk", "stale-nk",
                List.of(holding()), List.of());
        KisContinuationResult<KisBalanceResponse> continuationResult =
                new KisContinuationResult<>(kisBalanceResponse, false);

        given(accountQueryService.getAccountByUserId(userId)).willReturn(account);
        given(kisTokenCacheQueryService.getAccessToken(userId, "app-key", "secret-key", AccountType.REAL))
                .willReturn("issued-token");
        given(kisClient.inquireBalance(
                "issued-token", "app-key", "secret-key", "12345678", "01", AccountType.REAL,
                "prev-fk", "prev-nk"))
                .willReturn(continuationResult);

        // when
        AccountHoldingsResponse result = accountKisQueryService.getHoldings(userId, "prev-fk", "prev-nk");

        // then
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCtxAreaFk100()).isNull();
        assertThat(result.nextCtxAreaNk100()).isNull();
    }

    @Test
    @DisplayName("ctxAreaFk100/ctxAreaNk100 중 하나만 오면 INVALID_CONTINUATION_CURSOR 예외를 던지고 아무것도 조회하지 않는다")
    void getHoldingsFailsWhenOnlyOneCursorProvided() {
        // given
        UUID userId = UUID.randomUUID();

        // when & then
        assertThatThrownBy(() -> accountKisQueryService.getHoldings(userId, "only-fk", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.INVALID_CONTINUATION_CURSOR);
                });

        verifyNoInteractions(accountQueryService, kisTokenCacheQueryService, kisClient);
    }

    @Test
    @DisplayName("보유종목 조회 시 계좌가 없으면 ACCOUNT_NOT_FOUND 예외를 그대로 전파하고 KIS는 호출하지 않는다")
    void getHoldingsFailsWhenAccountNotFound() {
        // given
        UUID userId = UUID.randomUUID();
        given(accountQueryService.getAccountByUserId(userId))
                .willThrow(new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> accountKisQueryService.getHoldings(userId, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
                });

        verifyNoInteractions(kisTokenCacheQueryService, kisClient);
    }

    private static KisBalanceHoldingResponse holding() {
        return new KisBalanceHoldingResponse(
                "005930", // pdno
                "삼성전자", // prdt_name
                null, // trad_dvsn_name
                null, // bfdy_buy_qty
                null, // bfdy_sll_qty
                null, // thdt_buyqty
                null, // thdt_sll_qty
                "10", // hldg_qty
                "10", // ord_psbl_qty
                "70000.5", // pchs_avg_pric
                null, // pchs_amt
                "75000", // prpr
                "750000", // evlu_amt
                "49995", // evlu_pfls_amt
                "7.14", // evlu_pfls_rt
                null, // evlu_erng_rt
                null, // loan_dt
                null, // loan_amt
                null, // stln_slng_chgs
                null, // expd_dt
                null, // fltt_rt
                null, // bfdy_cprs_icdc
                null, // item_mgna_rt_name
                null, // grta_rt_name
                null, // sbst_pric
                null // stck_loan_unpr
        );
    }

    @Test
    @DisplayName("계좌 조회와 KIS 접근토큰 발급에 성공하면 accessToken과 계좌의 appKey/secretKey를 반환한다")
    void getKisAccessToken() {
        // given
        UUID userId = UUID.randomUUID();
        Account account = Account.createAccount(
                userId, "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);

        given(accountQueryService.getAccountByUserId(userId)).willReturn(account);
        given(kisTokenCacheQueryService.getAccessToken(userId, "app-key", "secret-key", AccountType.REAL))
                .willReturn("issued-token");

        // when
        AccessTokenResponse result = accountKisQueryService.getKisAccessToken(userId);

        // then
        assertThat(result.accessToken()).isEqualTo("issued-token");
        assertThat(result.appKey()).isEqualTo("app-key");
        assertThat(result.secretKey()).isEqualTo("secret-key");

        InOrder inOrder = inOrder(accountQueryService, kisTokenCacheQueryService);
        inOrder.verify(accountQueryService).getAccountByUserId(userId);
        inOrder.verify(kisTokenCacheQueryService).getAccessToken(userId, "app-key", "secret-key", AccountType.REAL);
    }

    @Test
    @DisplayName("계좌가 없으면 ACCOUNT_NOT_FOUND 예외를 그대로 전파하고 KIS는 호출하지 않는다")
    void getKisAccessTokenFailsWhenAccountNotFound() {
        // given
        UUID userId = UUID.randomUUID();
        given(accountQueryService.getAccountByUserId(userId))
                .willThrow(new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> accountKisQueryService.getKisAccessToken(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
                });

        verifyNoInteractions(kisTokenCacheQueryService);
    }

    @Test
    @DisplayName("KIS 토큰 발급에 실패하면 예외를 그대로 전파한다")
    void getKisAccessTokenFailsWhenKisTokenIssueFails() {
        // given
        UUID userId = UUID.randomUUID();
        Account account = Account.createAccount(
                userId, "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);

        given(accountQueryService.getAccountByUserId(userId)).willReturn(account);
        given(kisTokenCacheQueryService.getAccessToken(userId, "app-key", "secret-key", AccountType.REAL))
                .willThrow(new BusinessException(AccountErrorCode.KIS_TOKEN_ISSUE_FAILED));

        // when & then
        assertThatThrownBy(() -> accountKisQueryService.getKisAccessToken(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.KIS_TOKEN_ISSUE_FAILED);
                });
    }

    @Test
    @DisplayName("계좌 조회와 KIS 접속키 발급에 성공하면 approvalKey를 반환한다")
    void getKisApprovalKey() {
        // given
        UUID userId = UUID.randomUUID();
        Account account = Account.createAccount(
                userId, "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);

        given(accountQueryService.getAccountByUserId(userId)).willReturn(account);
        given(kisTokenCacheQueryService.getApprovalKey(userId, "app-key", "secret-key", AccountType.REAL))
                .willReturn("issued-approval-key");

        // when
        ApprovalKeyResponse result = accountKisQueryService.getKisApprovalKey(userId);

        // then
        assertThat(result.approvalKey()).isEqualTo("issued-approval-key");

        InOrder inOrder = inOrder(accountQueryService, kisTokenCacheQueryService);
        inOrder.verify(accountQueryService).getAccountByUserId(userId);
        inOrder.verify(kisTokenCacheQueryService).getApprovalKey(userId, "app-key", "secret-key", AccountType.REAL);
    }

    @Test
    @DisplayName("계좌가 없으면 접속키 발급도 ACCOUNT_NOT_FOUND 예외를 그대로 전파하고 KIS는 호출하지 않는다")
    void getKisApprovalKeyFailsWhenAccountNotFound() {
        // given
        UUID userId = UUID.randomUUID();
        given(accountQueryService.getAccountByUserId(userId))
                .willThrow(new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> accountKisQueryService.getKisApprovalKey(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
                });

        verifyNoInteractions(kisTokenCacheQueryService);
    }
}
