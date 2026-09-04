package com.sajo.user_service.account.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.controller.dto.response.AccessTokenResponse;
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

    private AccountKisQueryService accountKisQueryService;

    @BeforeEach
    void setUp() {
        accountKisQueryService = new AccountKisQueryService(accountQueryService, kisTokenCacheQueryService);
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
