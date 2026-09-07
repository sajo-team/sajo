package com.sajo.user_service.account.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.client.KisOAuthClient;
import com.sajo.user_service.account.client.dto.response.KisAccessTokenResponse;
import com.sajo.user_service.account.domain.Account;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.exception.AccountErrorCode;
import com.sajo.user_service.account.service.query.AccountQueryService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AccountCreateFacadeTest {

    @Mock
    private KisOAuthClient kisOAuthClient;

    @Mock
    private AccountQueryService accountQueryService;

    @Mock
    private AccountCommandService accountCommandService;

    @Mock
    private KisTokenCacheCommandService kisTokenCacheCommandService;

    private AccountCreateFacade accountCreateFacade;

    @BeforeEach
    void setUp() {
        accountCreateFacade =
                new AccountCreateFacade(kisOAuthClient, accountQueryService, accountCommandService, kisTokenCacheCommandService);
    }

    @Test
    @DisplayName("사전 체크, KIS 검증, 계좌 생성을 통과하면 검증 시 받은 토큰을 캐시에 채워 넣는다")
    void createAccount() {
        // given
        UUID userId = UUID.randomUUID();
        Account account = Account.createAccount(
                userId, "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);
        KisAccessTokenResponse kisResponse =
                new KisAccessTokenResponse("issued-token", "Bearer", 86400f, "2026-01-01 00:00:00");

        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL)).willReturn(kisResponse);
        given(accountCommandService.createAccount(userId, "app-key", "secret-key", "12345678-01", AccountType.REAL))
                .willReturn(account);

        // when
        Account result = accountCreateFacade.createAccount(
                userId, "app-key", "secret-key", "12345678-01", AccountType.REAL);

        // then
        assertThat(result).isEqualTo(account);

        InOrder inOrder = inOrder(accountQueryService, kisOAuthClient, accountCommandService, kisTokenCacheCommandService);
        inOrder.verify(accountQueryService).validateCreatable(userId, "12345678-01");
        inOrder.verify(kisOAuthClient).getAccessToken("app-key", "secret-key", AccountType.REAL);
        inOrder.verify(accountCommandService)
                .createAccount(userId, "app-key", "secret-key", "12345678-01", AccountType.REAL);
        inOrder.verify(kisTokenCacheCommandService)
                .primeKisAccessTokenCache(userId, "issued-token");
    }

    @Test
    @DisplayName("사전 중복 체크에서 실패하면 KIS 호출도, 계좌 생성도, 캐시 채우기도 하지 않는다")
    void createAccountFailsWhenPreCheckFails() {
        // given
        UUID userId = UUID.randomUUID();
        willThrow(new BusinessException(AccountErrorCode.ALREADY_HAS_ACCOUNT))
                .given(accountQueryService).validateCreatable(userId, "12345678-01");

        // when & then
        assertThatThrownBy(() -> accountCreateFacade.createAccount(
                userId, "app-key", "secret-key", "12345678-01", AccountType.REAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.ALREADY_HAS_ACCOUNT);
                });

        verifyNoInteractions(kisOAuthClient);
        verify(accountCommandService, never())
                .createAccount(any(), any(), any(), any(), any());
        verifyNoInteractions(kisTokenCacheCommandService);
    }

    @Test
    @DisplayName("KIS 자격증명 검증에 실패하면 계좌 생성도, 캐시 채우기도 하지 않는다")
    void createAccountFailsWhenKisCredentialsInvalid() {
        // given
        UUID userId = UUID.randomUUID();
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willThrow(new BusinessException(AccountErrorCode.INVALID_KIS_CREDENTIALS));

        // when & then
        assertThatThrownBy(() -> accountCreateFacade.createAccount(
                userId, "app-key", "secret-key", "12345678-01", AccountType.REAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.INVALID_KIS_CREDENTIALS);
                });

        verify(accountCommandService, never())
                .createAccount(any(), any(), any(), any(), any());
        verifyNoInteractions(kisTokenCacheCommandService);
    }

    @Test
    @DisplayName("KIS 검증 후 계좌 저장에 실패하면 캐시를 채우지 않는다")
    void createAccountDoesNotPrimeCacheWhenSaveFails() {
        // given
        UUID userId = UUID.randomUUID();
        KisAccessTokenResponse kisResponse =
                new KisAccessTokenResponse("issued-token", "Bearer", 86400f, "2026-01-01 00:00:00");

        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL)).willReturn(kisResponse);
        given(accountCommandService.createAccount(userId, "app-key", "secret-key", "12345678-01", AccountType.REAL))
                .willThrow(new BusinessException(AccountErrorCode.DUPLICATE_ACCOUNT_NO));

        // when & then
        assertThatThrownBy(() -> accountCreateFacade.createAccount(
                userId, "app-key", "secret-key", "12345678-01", AccountType.REAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.DUPLICATE_ACCOUNT_NO);
                });

        verifyNoInteractions(kisTokenCacheCommandService);
    }

    @Test
    @DisplayName("계좌 저장까지 성공한 뒤 캐시 프라이밍이 실패해도 계좌 생성 자체는 성공 처리한다")
    void createAccountSucceedsEvenWhenCachePrimingFails() {
        // given
        UUID userId = UUID.randomUUID();
        Account account = Account.createAccount(
                userId, "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);
        KisAccessTokenResponse kisResponse =
                new KisAccessTokenResponse("issued-token", "Bearer", 86400f, "2026-01-01 00:00:00");

        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL)).willReturn(kisResponse);
        given(accountCommandService.createAccount(userId, "app-key", "secret-key", "12345678-01", AccountType.REAL))
                .willReturn(account);
        willThrow(new RuntimeException("Redis 연결 실패"))
                .given(kisTokenCacheCommandService).primeKisAccessTokenCache(any(), any());

        // when
        Account result = accountCreateFacade.createAccount(
                userId, "app-key", "secret-key", "12345678-01", AccountType.REAL);

        // then
        assertThat(result).isEqualTo(account);
    }
}
