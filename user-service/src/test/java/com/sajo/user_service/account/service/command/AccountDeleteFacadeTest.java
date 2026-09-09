package com.sajo.user_service.account.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.client.kis.KisOAuthClient;
import com.sajo.user_service.account.client.feign.TradingFeignClient;
import com.sajo.user_service.account.client.feign.dto.response.TradingActiveStatusResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
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

    @Mock
    private KisTokenLogCommandService kisTokenLogCommandService;

    @Mock
    private TradingFeignClient tradingFeignClient;

    private AccountDeleteFacade accountDeleteFacade;

    @BeforeEach
    void setUp() {
        accountDeleteFacade = new AccountDeleteFacade(
                kisOAuthClient, cacheQueryService, cacheCommandService, accountCommandService,
                kisTokenLogCommandService, tradingFeignClient);
    }

    private void givenNoActiveTrading(UUID userId) {
        given(tradingFeignClient.getActiveStatus(userId)).willReturn(new TradingActiveStatusResponse(false));
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
        givenNoActiveTrading(userId);
        Account account = account(userId);
        given(accountCommandService.deleteAccount(userId)).willReturn(account);
        given(cacheQueryService.peekAccessToken(userId)).willReturn(Optional.of("cached-token"));

        // when
        accountDeleteFacade.deleteAccount(userId);

        // then
        verify(kisOAuthClient).revokeAccessToken("app-key", "secret-key", "cached-token", AccountType.REAL);
        verify(cacheCommandService).evictKisTokenCaches(userId);
        verify(kisTokenLogCommandService).recordRevokeSuccess(account.getId(), userId);
    }

    @Test
    @DisplayName("캐시에 접근토큰이 없으면 KIS 폐기 요청 없이 캐시 제거만 한다")
    void deleteAccountSkipsRevokeWhenNoCachedToken() {
        // given
        UUID userId = UUID.randomUUID();
        givenNoActiveTrading(userId);
        Account account = account(userId);
        given(accountCommandService.deleteAccount(userId)).willReturn(account);
        given(cacheQueryService.peekAccessToken(userId)).willReturn(Optional.empty());

        // when
        accountDeleteFacade.deleteAccount(userId);

        // then
        verifyNoInteractions(kisOAuthClient);
        verify(cacheCommandService).evictKisTokenCaches(userId);
        verifyNoInteractions(kisTokenLogCommandService);
    }

    @Test
    @DisplayName("계좌 삭제(DB)가 실패하면 ACTIVE로 되돌리고, KIS 폐기/캐시 제거는 시도하지 않은 채 예외를 그대로 전파한다")
    void deleteAccountReactivatesAndPropagatesFailureWhenDbDeleteFails() {
        // given
        UUID userId = UUID.randomUUID();
        givenNoActiveTrading(userId);
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

        // markPendingDeletion이 이미 커밋된 뒤라, 삭제 실패해도 반드시 원상복구해야 한다
        verify(accountCommandService).reactivate(userId);
        verifyNoInteractions(kisOAuthClient);
        verifyNoInteractions(cacheQueryService);
        verifyNoInteractions(cacheCommandService);
        verifyNoInteractions(kisTokenLogCommandService);
    }

    @Test
    @DisplayName("계좌 삭제(DB) 실패 후 reactivate 자체도 실패하면, 원본 예외를 유실하지 않고 그대로 전파한다")
    void deleteAccountPreservesOriginalExceptionWhenReactivateAlsoFailsAfterDbDeleteFailure() {
        // given
        UUID userId = UUID.randomUUID();
        givenNoActiveTrading(userId);
        BusinessException dbDeleteFailure = new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND);
        RuntimeException reactivateFailure = new RuntimeException("DB 커넥션 끊김");
        willThrow(dbDeleteFailure).given(accountCommandService).deleteAccount(userId);
        willThrow(reactivateFailure).given(accountCommandService).reactivate(userId);

        // when & then
        assertThatThrownBy(() -> accountDeleteFacade.deleteAccount(userId))
                .isSameAs(dbDeleteFailure)
                .satisfies(exception ->
                        assertThat(exception.getSuppressed()).contains(reactivateFailure));

        verifyNoInteractions(kisOAuthClient);
        verifyNoInteractions(cacheQueryService);
        verifyNoInteractions(cacheCommandService);
        verifyNoInteractions(kisTokenLogCommandService);
    }

    @Test
    @DisplayName("KIS 폐기 요청이 실패해도 계좌 삭제는 이미 끝난 상태라 예외 없이 캐시 제거까지 진행한다")
    void deleteAccountSucceedsEvenWhenRevokeFails() {
        // given
        UUID userId = UUID.randomUUID();
        givenNoActiveTrading(userId);
        Account account = account(userId);
        given(accountCommandService.deleteAccount(userId)).willReturn(account);
        given(cacheQueryService.peekAccessToken(userId)).willReturn(Optional.of("cached-token"));
        willThrow(new BusinessException(AccountErrorCode.KIS_TOKEN_ISSUE_FAILED))
                .given(kisOAuthClient).revokeAccessToken("app-key", "secret-key", "cached-token", AccountType.REAL);

        // when & then
        assertThatCode(() -> accountDeleteFacade.deleteAccount(userId)).doesNotThrowAnyException();

        verify(cacheCommandService).evictKisTokenCaches(userId);
        verify(kisTokenLogCommandService).recordRevokeFail(eq(account.getId()), eq(userId), isNull(), any());
    }

    @Test
    @DisplayName("캐시된 토큰 조회(Redis) 자체가 실패하면 KIS 폐기를 시도하지 않고, 폐기 실패로 잘못 기록하지도 않는다")
    void deleteAccountDoesNotRecordRevokeFailWhenPeekAccessTokenFails() {
        // given
        UUID userId = UUID.randomUUID();
        givenNoActiveTrading(userId);
        Account account = account(userId);
        given(accountCommandService.deleteAccount(userId)).willReturn(account);
        willThrow(new RuntimeException("Redis 타임아웃"))
                .given(cacheQueryService).peekAccessToken(userId);

        // when & then
        assertThatCode(() -> accountDeleteFacade.deleteAccount(userId)).doesNotThrowAnyException();

        verifyNoInteractions(kisOAuthClient);
        verify(cacheCommandService).evictKisTokenCaches(userId);
        verifyNoInteractions(kisTokenLogCommandService);
    }

    @Test
    @DisplayName("캐시 제거가 실패해도 예외 없이 정상 종료한다")
    void deleteAccountSucceedsEvenWhenCacheEvictFails() {
        // given
        UUID userId = UUID.randomUUID();
        givenNoActiveTrading(userId);
        Account account = account(userId);
        given(accountCommandService.deleteAccount(userId)).willReturn(account);
        given(cacheQueryService.peekAccessToken(userId)).willReturn(Optional.empty());
        willThrow(new RuntimeException("Redis 연결 실패"))
                .given(cacheCommandService).evictKisTokenCaches(userId);

        // when & then
        assertThatCode(() -> accountDeleteFacade.deleteAccount(userId)).doesNotThrowAnyException();

        verify(accountCommandService).deleteAccount(userId);
    }

    @Test
    @DisplayName("진행 중인 자동매매 또는 미체결 주문이 있으면 계좌 삭제를 시도하지 않고 예외를 던진다")
    void deleteAccountThrowsWhenActiveTradingExists() {
        // given
        UUID userId = UUID.randomUUID();
        given(tradingFeignClient.getActiveStatus(userId)).willReturn(new TradingActiveStatusResponse(true));

        // when & then
        assertThatThrownBy(() -> accountDeleteFacade.deleteAccount(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.ACTIVE_TRADING_EXISTS);
                });

        // 활성 거래 확인 전 PENDING_DELETION으로 표시했다가, 차단되면 다시 ACTIVE로 되돌린다
        verify(accountCommandService).markPendingDeletion(userId);
        verify(accountCommandService).reactivate(userId);
        verify(accountCommandService, never()).deleteAccount(any());
        verifyNoInteractions(kisOAuthClient);
        verifyNoInteractions(cacheQueryService);
        verifyNoInteractions(cacheCommandService);
        verifyNoInteractions(kisTokenLogCommandService);
    }

    @Test
    @DisplayName("trading-service 호출 자체가 실패해도 계좌를 ACTIVE로 되돌리고 예외를 그대로 전파한다")
    void deleteAccountReactivatesWhenTradingServiceCallFails() {
        // given
        UUID userId = UUID.randomUUID();
        RuntimeException tradingServiceFailure = new RuntimeException("trading-service 타임아웃");
        given(tradingFeignClient.getActiveStatus(userId)).willThrow(tradingServiceFailure);

        // when & then
        assertThatThrownBy(() -> accountDeleteFacade.deleteAccount(userId))
                .isSameAs(tradingServiceFailure);

        // PENDING_DELETION에 영구히 고착되지 않도록 반드시 원상복구해야 한다
        verify(accountCommandService).markPendingDeletion(userId);
        verify(accountCommandService).reactivate(userId);
        verify(accountCommandService, never()).deleteAccount(any());
        verifyNoInteractions(kisOAuthClient);
        verifyNoInteractions(cacheQueryService);
        verifyNoInteractions(cacheCommandService);
        verifyNoInteractions(kisTokenLogCommandService);
    }

    @Test
    @DisplayName("trading-service 호출 실패 후 reactivate 자체도 실패하면, 원본 예외를 유실하지 않고 그대로 전파한다")
    void deleteAccountPreservesOriginalExceptionWhenReactivateAlsoFails() {
        // given
        UUID userId = UUID.randomUUID();
        RuntimeException tradingServiceFailure = new RuntimeException("trading-service 타임아웃");
        RuntimeException reactivateFailure = new RuntimeException("DB 커넥션 끊김");
        given(tradingFeignClient.getActiveStatus(userId)).willThrow(tradingServiceFailure);
        willThrow(reactivateFailure).given(accountCommandService).reactivate(userId);

        // when & then
        assertThatThrownBy(() -> accountDeleteFacade.deleteAccount(userId))
                .isSameAs(tradingServiceFailure)
                .satisfies(exception ->
                        assertThat(exception.getSuppressed()).contains(reactivateFailure));

        verify(accountCommandService, never()).deleteAccount(any());
        verifyNoInteractions(kisOAuthClient);
        verifyNoInteractions(cacheQueryService);
        verifyNoInteractions(cacheCommandService);
        verifyNoInteractions(kisTokenLogCommandService);
    }
}
