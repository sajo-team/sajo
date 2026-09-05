package com.sajo.user_service.account.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.client.KisOAuthClient;
import com.sajo.user_service.account.domain.Account;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.exception.AccountErrorCode;
import com.sajo.user_service.account.service.query.KisTokenCacheQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AccountDeleteFacadeTest {

    @Mock
    private KisOAuthClient kisOAuthClient;

    @Mock
    private KisTokenCacheQueryService cacheQueryService;

    @Mock
    private KisTokenCacheCommandService cacheCommandService;

    @Mock
    private AccountCommandService accountCommandService;

    private AccountDeleteFacade accountDeleteFacade;

    @BeforeEach
    void setUp() {
        accountDeleteFacade =
                new AccountDeleteFacade(kisOAuthClient, cacheQueryService, cacheCommandService, accountCommandService);
    }

    private Account account(UUID userId) {
        return Account.createAccount(
                userId, "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);
    }

    @Test
    @DisplayName("캐시에 접근토큰이 있으면 계좌 삭제 후 그 토큰으로 KIS 폐기 요청과 캐시 제거를 한다")
    void deleteAccountRevokesCachedTokenAndEvictsCache() {
        // given
        UUID userId = UUID.randomUUID();
        Account account = account(userId);
        given(accountCommandService.deleteAccount(userId)).willReturn(account);
        given(cacheQueryService.peekAccessToken(userId)).willReturn(Optional.of("cached-token"));

        // when
        accountDeleteFacade.deleteAccount(userId);

        // then
        verify(kisOAuthClient).revokeAccessToken("app-key", "secret-key", "cached-token", AccountType.REAL);
        verify(cacheCommandService).evictKisTokenCaches(userId);
    }

    @Test
    @DisplayName("캐시에 접근토큰이 없으면 KIS 폐기 요청 없이 캐시 제거만 한다")
    void deleteAccountSkipsRevokeWhenNoCachedToken() {
        // given
        UUID userId = UUID.randomUUID();
        Account account = account(userId);
        given(accountCommandService.deleteAccount(userId)).willReturn(account);
        given(cacheQueryService.peekAccessToken(userId)).willReturn(Optional.empty());

        // when
        accountDeleteFacade.deleteAccount(userId);

        // then
        verifyNoInteractions(kisOAuthClient);
        verify(cacheCommandService).evictKisTokenCaches(userId);
    }

    @Test
    @DisplayName("계좌 삭제(DB)가 실패하면 KIS 폐기도 캐시 제거도 시도하지 않고 예외를 그대로 전파한다")
    void deleteAccountPropagatesFailureWithoutSideEffectsWhenDbDeleteFails() {
        // given
        UUID userId = UUID.randomUUID();
        willThrow(new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND))
                .given(accountCommandService).deleteAccount(userId);

        // when & then
        assertThatThrownBy(() -> accountDeleteFacade.deleteAccount(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
                });

        verifyNoInteractions(kisOAuthClient);
        verifyNoInteractions(cacheQueryService);
        verifyNoInteractions(cacheCommandService);
    }

    @Test
    @DisplayName("KIS 폐기 요청이 실패해도 계좌 삭제는 이미 끝난 상태라 예외 없이 캐시 제거까지 진행한다")
    void deleteAccountSucceedsEvenWhenRevokeFails() {
        // given
        UUID userId = UUID.randomUUID();
        Account account = account(userId);
        given(accountCommandService.deleteAccount(userId)).willReturn(account);
        given(cacheQueryService.peekAccessToken(userId)).willReturn(Optional.of("cached-token"));
        willThrow(new BusinessException(AccountErrorCode.KIS_TOKEN_ISSUE_FAILED))
                .given(kisOAuthClient).revokeAccessToken("app-key", "secret-key", "cached-token", AccountType.REAL);

        // when & then
        assertThatCode(() -> accountDeleteFacade.deleteAccount(userId)).doesNotThrowAnyException();

        verify(cacheCommandService).evictKisTokenCaches(userId);
    }

    @Test
    @DisplayName("캐시 제거가 실패해도 예외 없이 정상 종료한다")
    void deleteAccountSucceedsEvenWhenCacheEvictFails() {
        // given
        UUID userId = UUID.randomUUID();
        Account account = account(userId);
        given(accountCommandService.deleteAccount(userId)).willReturn(account);
        given(cacheQueryService.peekAccessToken(userId)).willReturn(Optional.empty());
        willThrow(new RuntimeException("Redis 연결 실패"))
                .given(cacheCommandService).evictKisTokenCaches(userId);

        // when & then
        assertThatCode(() -> accountDeleteFacade.deleteAccount(userId)).doesNotThrowAnyException();

        verify(accountCommandService).deleteAccount(userId);
    }
}
