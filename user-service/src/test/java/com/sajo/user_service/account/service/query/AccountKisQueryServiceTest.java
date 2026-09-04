package com.sajo.user_service.account.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.client.KisClient;
import com.sajo.user_service.account.client.dto.response.KisBalanceResponse;
import com.sajo.user_service.account.client.dto.response.KisBalanceSummaryResponse;
import com.sajo.user_service.account.controller.dto.response.AccessTokenResponse;
import com.sajo.user_service.account.controller.dto.response.AccountDepositResponse;
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
