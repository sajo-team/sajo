package com.sajo.user_service.account.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.cache.KisTokenCacheKeys;
import com.sajo.user_service.account.cache.KisTokenEntry;
import com.sajo.user_service.account.cache.KisTokenLocalCache;
import com.sajo.user_service.account.cache.KisTokenRemoteCache;
import com.sajo.user_service.account.cache.RedisUnavailableException;
import com.sajo.user_service.account.client.kis.KisOAuthClient;
import com.sajo.user_service.account.client.kis.dto.response.KisAccessTokenResponse;
import com.sajo.user_service.account.client.kis.dto.response.KisApprovalKeyResponse;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.domain.KisTokenType;
import com.sajo.user_service.account.exception.AccountErrorCode;
import com.sajo.user_service.account.exception.KisBusinessException;
import com.sajo.user_service.account.service.command.KisTokenLogCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// KisTokenCacheQueryService는 이제 L1(KisTokenLocalCache)/L2(KisTokenRemoteCache) 조합을
// 오케스트레이션하는 순수 흐름만 갖고 있다. 그래서 이 테스트는 localCache.getOrLoad가 항상
// loader(=loadFromRemoteOrKis)를 그대로 실행하게 스텁해서 "L1은 항상 미스"인 상태로 두고,
// L2(remoteCache) 이후의 흐름(분산락/fail-open/이력 기록)만 검증한다. L1 자체의 동작
// (single-flight, 만료, 실패 재시도)은 KisTokenLocalCacheTest에서 별도로 검증한다.
@ExtendWith(MockitoExtension.class)
class KisTokenCacheQueryServiceTest {

    @Mock
    private KisOAuthClient kisOAuthClient;

    @Mock
    private KisTokenLogCommandService kisTokenLogCommandService;

    @Mock
    private KisTokenLocalCache localCache;

    @Mock
    private KisTokenRemoteCache remoteCache;

    private KisTokenCacheQueryService kisTokenCacheQueryService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kisTokenCacheQueryService = new KisTokenCacheQueryService(
                kisOAuthClient, kisTokenLogCommandService, localCache, remoteCache);

        // L1은 항상 미스로 취급하고, 넘겨받은 loader를 그대로 실행한 결과를 반환한다.
        // peekAccessToken 테스트들은 이 스텁을 안 쓰므로 lenient로 strict-stubbing 오탐을 막는다.
        org.mockito.Mockito.lenient().when(localCache.getOrLoad(anyString(), any())).thenAnswer(invocation -> {
            Supplier<KisTokenEntry> loader = invocation.getArgument(1);
            return loader.get();
        });
    }

    @Test
    @DisplayName("L2(Redis) 캐시에 값이 있으면 KIS 호출/분산락 없이 그 값을 반환한다")
    void getAccessToken_remoteCacheHit_returnsCachedValueWithoutCallingKis() {
        // given
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(accountId);
        given(remoteCache.get(key)).willReturn(Optional.of(new KisTokenEntry("cached-token", Duration.ofHours(1))));

        // when
        String result = kisTokenCacheQueryService.getAccessToken(userId, accountId, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(result).isEqualTo("cached-token");
        verifyNoInteractions(kisOAuthClient);
        verify(remoteCache, never()).tryLock(any(), any());
    }

    @Test
    @DisplayName("L2 캐시 미스 시 락을 잡고 KIS를 호출해 값을 저장한 뒤 반환하고, 락은 반드시 해제한다")
    void getAccessToken_remoteCacheMiss_fetchesFromKisAndCaches() {
        // given
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(accountId);
        given(remoteCache.get(key)).willReturn(Optional.empty());
        given(remoteCache.tryLock(eq(key), anyString())).willReturn(true);
        given(remoteCache.recentFailure(key)).willReturn(Optional.empty());
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("issued-token", "Bearer", 86400, "2026-01-01 00:00:00"));

        // when
        String result = kisTokenCacheQueryService.getAccessToken(userId, accountId, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(result).isEqualTo("issued-token");
        verify(remoteCache).save(eq(key), eq(new KisTokenEntry("issued-token", Duration.ofSeconds(86400 - 60))));
        verify(kisTokenLogCommandService).recordSuccess(accountId, userId, KisTokenType.ACCESS_TOKEN);
        // 저장 직후 콜백으로 한 번, 호출부 finally 안전망으로 한 번 - 총 2번 (중복 해제는 무해함)
        verify(remoteCache, org.mockito.Mockito.times(2)).unlock(eq(key), anyString());
    }

    @Test
    @DisplayName("락이 실제로 보호해야 하는 작업(저장/해제)이 끝난 뒤에야 DB 이력 기록(부가 작업)이 일어난다")
    void getAccessToken_releasesLockBeforeRecordingSuccess() {
        // given
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(accountId);
        given(remoteCache.get(key)).willReturn(Optional.empty());
        given(remoteCache.tryLock(eq(key), anyString())).willReturn(true);
        given(remoteCache.recentFailure(key)).willReturn(Optional.empty());
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("issued-token", "Bearer", 86400, "2026-01-01 00:00:00"));

        // when
        kisTokenCacheQueryService.getAccessToken(userId, accountId, "app-key", "secret-key", AccountType.REAL);

        // then
        InOrder inOrder = inOrder(remoteCache, kisTokenLogCommandService);
        inOrder.verify(remoteCache).save(eq(key), any());
        inOrder.verify(remoteCache).unlock(eq(key), anyString());
        inOrder.verify(kisTokenLogCommandService).recordSuccess(accountId, userId, KisTokenType.ACCESS_TOKEN);
    }

    @Test
    @DisplayName("KIS 호출이 실패하면 실패 마커를 남기고 실패 이력을 기록하며 예외를 그대로 전파하고, 락은 반드시 해제한다")
    void getAccessToken_kisFails_marksFailureRecordsAndPropagatesAndUnlocks() {
        // given
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(accountId);
        KisBusinessException kisException =
                new KisBusinessException(AccountErrorCode.INVALID_KIS_CREDENTIALS, "EGW00123", "유효하지 않은 앱키입니다.");

        given(remoteCache.get(key)).willReturn(Optional.empty());
        given(remoteCache.tryLock(eq(key), anyString())).willReturn(true);
        given(remoteCache.recentFailure(key)).willReturn(Optional.empty());
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL)).willThrow(kisException);

        // when & then
        assertThatThrownBy(() ->
                kisTokenCacheQueryService.getAccessToken(userId, accountId, "app-key", "secret-key", AccountType.REAL))
                .isSameAs(kisException);

        verify(remoteCache).markRecentFailure(key, AccountErrorCode.INVALID_KIS_CREDENTIALS);
        verify(kisTokenLogCommandService)
                .recordFail(accountId, userId, KisTokenType.ACCESS_TOKEN, "EGW00123", "유효하지 않은 앱키입니다.");
        verify(remoteCache, never()).save(any(), any());
        verify(remoteCache, org.mockito.Mockito.times(2)).unlock(eq(key), anyString());
    }

    @Test
    @DisplayName("직전 락 홀더가 방금 실패해 실패 마커가 남아있으면, KIS 호출 없이 그 종류 그대로 즉시 실패한다")
    void getAccessToken_recentFailureMarkerExists_failsFastWithoutCallingKis() {
        // given
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(accountId);
        given(remoteCache.get(key)).willReturn(Optional.empty());
        given(remoteCache.tryLock(eq(key), anyString())).willReturn(true);
        given(remoteCache.recentFailure(key)).willReturn(Optional.of(AccountErrorCode.KIS_RATE_LIMITED));

        // when & then
        assertThatThrownBy(() ->
                kisTokenCacheQueryService.getAccessToken(userId, accountId, "app-key", "secret-key", AccountType.REAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(AccountErrorCode.KIS_RATE_LIMITED);
                });

        verifyNoInteractions(kisOAuthClient);
        verify(remoteCache).unlock(eq(key), anyString());
    }

    @Test
    @DisplayName("락을 못 잡아도 대기 중 다른 요청이 캐시를 채우면 KIS 호출 없이 그 값을 반환한다")
    void getAccessToken_lockNotAcquired_returnsValueFilledByAnotherHolder() {
        // given
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(accountId);
        given(remoteCache.get(key))
                .willReturn(Optional.empty()) // 최초 조회
                .willReturn(Optional.of(new KisTokenEntry("filled-by-other", Duration.ofHours(1)))); // 대기 중 재확인
        given(remoteCache.tryLock(eq(key), anyString())).willReturn(false);

        // when
        String result = kisTokenCacheQueryService.getAccessToken(userId, accountId, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(result).isEqualTo("filled-by-other");
        verifyNoInteractions(kisOAuthClient);
    }

    @Test
    @DisplayName("락을 계속 못 잡고 캐시도 안 채워지면 타임아웃 예외를 던진다")
    void getAccessToken_lockNeverAcquired_throwsTimeoutException() {
        // given - KisTokenCacheLock.WAIT_TIMEOUT(42s)을 다 채우는 실제 대기가 일어나 다른 테스트보다 느리다
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(accountId);
        given(remoteCache.get(key)).willReturn(Optional.empty());
        given(remoteCache.tryLock(eq(key), anyString())).willReturn(false);

        // when & then
        assertThatThrownBy(() ->
                kisTokenCacheQueryService.getAccessToken(userId, accountId, "app-key", "secret-key", AccountType.REAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.KIS_TOKEN_CACHE_LOCK_TIMEOUT);
                });

        verifyNoInteractions(kisOAuthClient);
    }

    @Test
    @DisplayName("초기 조회에서 Redis 장애가 나면 락 없이 바로 KIS를 직접 호출한다 (fail-open)")
    void getAccessToken_initialLookupRedisUnavailable_fallsOpenToKis() {
        // given
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(accountId);
        given(remoteCache.get(key)).willThrow(new RedisUnavailableException(new RuntimeException("연결 실패")));
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("issued-token", "Bearer", 86400, "2026-01-01 00:00:00"));

        // when
        String result = kisTokenCacheQueryService.getAccessToken(userId, accountId, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(result).isEqualTo("issued-token");
        verify(remoteCache, never()).tryLock(any(), any());
        verify(remoteCache, never()).unlock(any(), any());
    }

    @Test
    @DisplayName("락 획득 시도에서 Redis 장애가 나면 락 없이 바로 KIS를 직접 호출한다 (fail-open)")
    void getAccessToken_tryLockRedisUnavailable_fallsOpenToKis() {
        // given
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(accountId);
        given(remoteCache.get(key)).willReturn(Optional.empty());
        given(remoteCache.tryLock(eq(key), anyString()))
                .willThrow(new RedisUnavailableException(new RuntimeException("연결 실패")));
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("issued-token", "Bearer", 86400, "2026-01-01 00:00:00"));

        // when
        String result = kisTokenCacheQueryService.getAccessToken(userId, accountId, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(result).isEqualTo("issued-token");
        verify(remoteCache, never()).unlock(any(), any());
    }

    @Test
    @DisplayName("expires_in이 안전마진보다 작아 TTL이 0이면 캐시에 저장하지 않는다")
    void getAccessToken_expiresInTooSmall_skipsCaching() {
        // given
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(accountId);
        given(remoteCache.get(key)).willReturn(Optional.empty());
        given(remoteCache.tryLock(eq(key), anyString())).willReturn(true);
        given(remoteCache.recentFailure(key)).willReturn(Optional.empty());
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("issued-token", "Bearer", 30, "2026-01-01 00:00:00"));

        // when
        String result = kisTokenCacheQueryService.getAccessToken(userId, accountId, "app-key", "secret-key", AccountType.REAL);

        // then - save 자체는 KisTokenRemoteCache.save 내부에서 ttl==0이면 저장을 건너뛴다 (여기선 호출까지만 확인)
        assertThat(result).isEqualTo("issued-token");
        verify(remoteCache).save(eq(key), eq(new KisTokenEntry("issued-token", Duration.ZERO)));
    }

    @Test
    @DisplayName("접속키도 동일한 흐름을 탄다 - L2 미스 시 KIS 호출 후 저장한다")
    void getApprovalKey_remoteCacheMiss_fetchesFromKisAndCaches() {
        // given
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String key = KisTokenCacheKeys.approvalKey(accountId);
        given(remoteCache.get(key)).willReturn(Optional.empty());
        given(remoteCache.tryLock(eq(key), anyString())).willReturn(true);
        given(remoteCache.recentFailure(key)).willReturn(Optional.empty());
        given(kisOAuthClient.getApprovalKey("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisApprovalKeyResponse("issued-approval-key"));

        // when
        String result = kisTokenCacheQueryService.getApprovalKey(userId, accountId, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(result).isEqualTo("issued-approval-key");
        verify(kisTokenLogCommandService).recordSuccess(accountId, userId, KisTokenType.APPROVAL_KEY);
    }

    @Test
    @DisplayName("L1(로컬)에 값이 있으면 그 값을 담은 Optional을 반환하고 L2는 조회하지 않는다")
    void peekAccessToken_localCacheHit_returnsWithoutTouchingRemote() {
        // given
        UUID accountId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(accountId);
        given(localCache.peek(key)).willReturn(Optional.of("local-token"));

        // when
        Optional<String> result = kisTokenCacheQueryService.peekAccessToken(accountId);

        // then
        assertThat(result).contains("local-token");
        verifyNoInteractions(remoteCache);
    }

    @Test
    @DisplayName("L1이 비어있으면 L2(Redis)를 조회해서 반환한다")
    void peekAccessToken_localCacheMiss_fallsBackToRemote() {
        // given
        UUID accountId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(accountId);
        given(localCache.peek(key)).willReturn(Optional.empty());
        given(remoteCache.get(key)).willReturn(Optional.of(new KisTokenEntry("remote-token", Duration.ofHours(1))));

        // when
        Optional<String> result = kisTokenCacheQueryService.peekAccessToken(accountId);

        // then
        assertThat(result).contains("remote-token");
    }

    @Test
    @DisplayName("L1/L2 둘 다 비어있으면 빈 Optional을 반환한다")
    void peekAccessToken_bothCachesMiss_returnsEmpty() {
        // given
        UUID accountId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(accountId);
        given(localCache.peek(key)).willReturn(Optional.empty());
        given(remoteCache.get(key)).willReturn(Optional.empty());

        // when
        Optional<String> result = kisTokenCacheQueryService.peekAccessToken(accountId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("L2 조회 중 Redis 장애가 나도 예외 없이 빈 Optional을 반환한다")
    void peekAccessToken_remoteCacheUnavailable_returnsEmpty() {
        // given
        UUID accountId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(accountId);
        given(localCache.peek(key)).willReturn(Optional.empty());
        given(remoteCache.get(key)).willThrow(new RedisUnavailableException(new RuntimeException("타임아웃")));

        // when
        Optional<String> result = kisTokenCacheQueryService.peekAccessToken(accountId);

        // then
        assertThat(result).isEmpty();
    }
}
