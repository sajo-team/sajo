package com.sajo.user_service.account.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.cache.KisTokenCacheKeys;
import com.sajo.user_service.account.cache.KisTokenCacheLock;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class KisTokenCacheQueryServiceTest {

    @Mock
    private KisOAuthClient kisOAuthClient;

    @Mock
    private KisTokenLogCommandService kisTokenLogCommandService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private KisTokenCacheLock kisTokenCacheLock;

    private KisTokenCacheQueryService kisTokenCacheQueryService;

    @BeforeEach
    void setUp() {
        kisTokenCacheQueryService = new KisTokenCacheQueryService(
                kisOAuthClient, kisTokenLogCommandService, redisTemplate, kisTokenCacheLock);
    }

    @Test
    @DisplayName("캐시에 값이 있으면 KIS 호출 없이 그 값을 반환한다")
    void getAccessToken_cacheHit_returnsCachedValueWithoutCallingKis() {
        // given
        UUID userId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(key)).willReturn("cached-token");

        // when
        String result = kisTokenCacheQueryService.getAccessToken(userId, null, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(result).isEqualTo("cached-token");
        verifyNoInteractions(kisOAuthClient);
        verifyNoInteractions(kisTokenCacheLock);
    }

    @Test
    @DisplayName("캐시 미스 시 락을 잡고 KIS를 호출해 값을 캐시에 저장한 뒤 반환하고, 락은 반드시 해제한다")
    void getAccessToken_cacheMiss_fetchesFromKisAndCaches() {
        // given
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(key)).willReturn(null);
        given(kisTokenCacheLock.tryLock(eq(key), anyString(), any(Duration.class))).willReturn(true);
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("issued-token", "Bearer", 86400, "2026-01-01 00:00:00"));

        // when
        String result = kisTokenCacheQueryService.getAccessToken(userId, accountId, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(result).isEqualTo("issued-token");
        verify(valueOperations).set(eq(key), eq("issued-token"), any(Duration.class));
        verify(kisTokenLogCommandService).recordSuccess(accountId, userId, KisTokenType.ACCESS_TOKEN);
        verify(kisTokenCacheLock).unlock(eq(key), anyString());
    }

    @Test
    @DisplayName("KIS 호출이 실패하면 실패 이력을 남기고 예외를 그대로 전파하며, 락은 반드시 해제한다")
    void getAccessToken_kisFails_recordsFailureAndPropagatesAndUnlocks() {
        // given
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(userId);
        KisBusinessException kisException =
                new KisBusinessException(AccountErrorCode.INVALID_KIS_CREDENTIALS, "EGW00123", "유효하지 않은 앱키입니다.");

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(key)).willReturn(null);
        given(kisTokenCacheLock.tryLock(eq(key), anyString(), any(Duration.class))).willReturn(true);
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL)).willThrow(kisException);

        // when & then
        assertThatThrownBy(() ->
                kisTokenCacheQueryService.getAccessToken(userId, accountId, "app-key", "secret-key", AccountType.REAL))
                .isSameAs(kisException);

        verify(kisTokenLogCommandService)
                .recordFail(accountId, userId, KisTokenType.ACCESS_TOKEN, "EGW00123", "유효하지 않은 앱키입니다.");
        verify(kisTokenCacheLock).unlock(eq(key), anyString());
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("락을 못 잡아도 대기 중 다른 요청이 캐시를 채우면 KIS 호출 없이 그 값을 반환한다")
    void getAccessToken_lockNotAcquired_returnsValueFilledByAnotherHolder() {
        // given
        UUID userId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(key))
                .willReturn(null)               // 최초 조회
                .willReturn("filled-by-other"); // 락 못 잡고 대기 중 재확인
        given(kisTokenCacheLock.tryLock(eq(key), anyString(), any(Duration.class))).willReturn(false);

        // when
        String result = kisTokenCacheQueryService.getAccessToken(userId, null, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(result).isEqualTo("filled-by-other");
        verifyNoInteractions(kisOAuthClient);
    }

    @Test
    @DisplayName("락을 계속 못 잡고 캐시도 안 채워지면 타임아웃 예외를 던진다")
    void getAccessToken_lockNeverAcquired_throwsTimeoutException() {
        // given - LOCK_WAIT_TIMEOUT(5s) 다 채우는 실제 대기가 일어나 다른 테스트보다 느리다
        UUID userId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(key)).willReturn(null);
        given(kisTokenCacheLock.tryLock(eq(key), anyString(), any(Duration.class))).willReturn(false);

        // when & then
        assertThatThrownBy(() ->
                kisTokenCacheQueryService.getAccessToken(userId, null, "app-key", "secret-key", AccountType.REAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.KIS_TOKEN_CACHE_LOCK_TIMEOUT);
                });

        verifyNoInteractions(kisOAuthClient);
    }

    @Test
    @DisplayName("초기 캐시 조회 시 Redis 장애가 나면 락 없이 바로 KIS를 직접 호출한다 (fail-open)")
    void getAccessToken_initialLookupRedisFails_fallsOpenToKis() {
        // given
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(key)).willThrow(new RedisConnectionFailureException("연결 실패"));
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("issued-token", "Bearer", 86400, "2026-01-01 00:00:00"));

        // when
        String result = kisTokenCacheQueryService.getAccessToken(userId, accountId, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(result).isEqualTo("issued-token");
        verifyNoInteractions(kisTokenCacheLock);
    }

    @Test
    @DisplayName("락 획득 시 Redis 장애가 나면 락 없이 바로 KIS를 직접 호출한다 (fail-open)")
    void getAccessToken_tryLockRedisFails_fallsOpenToKis() {
        // given
        UUID userId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(key)).willReturn(null);
        given(kisTokenCacheLock.tryLock(eq(key), anyString(), any(Duration.class)))
                .willThrow(new RedisConnectionFailureException("연결 실패"));
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("issued-token", "Bearer", 86400, "2026-01-01 00:00:00"));

        // when
        String result = kisTokenCacheQueryService.getAccessToken(userId, null, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(result).isEqualTo("issued-token");
        verify(kisTokenCacheLock, never()).unlock(any(), any());
    }

    @Test
    @DisplayName("캐시 저장 시 Redis 장애가 나도 발급받은 토큰은 정상 반환한다")
    void getAccessToken_cacheSaveFails_stillReturnsToken() {
        // given
        UUID userId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(key)).willReturn(null);
        given(kisTokenCacheLock.tryLock(eq(key), anyString(), any(Duration.class))).willReturn(true);
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("issued-token", "Bearer", 86400, "2026-01-01 00:00:00"));
        willThrow(new RedisConnectionFailureException("연결 실패"))
                .given(valueOperations).set(eq(key), eq("issued-token"), any(Duration.class));

        // when
        String result = kisTokenCacheQueryService.getAccessToken(userId, null, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(result).isEqualTo("issued-token");
    }

    @Test
    @DisplayName("expires_in이 안전마진보다 작아 TTL이 0이면 캐시에 저장하지 않는다")
    void getAccessToken_expiresInTooSmall_skipsCaching() {
        // given
        UUID userId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(key)).willReturn(null);
        given(kisTokenCacheLock.tryLock(eq(key), anyString(), any(Duration.class))).willReturn(true);
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("issued-token", "Bearer", 30, "2026-01-01 00:00:00"));

        // when
        String result = kisTokenCacheQueryService.getAccessToken(userId, null, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(result).isEqualTo("issued-token");
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("접속키도 동일한 락+캐시 흐름을 탄다 - 캐시 미스 시 KIS 호출 후 저장한다")
    void getApprovalKey_cacheMiss_fetchesFromKisAndCaches() {
        // given
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String key = KisTokenCacheKeys.approvalKey(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(key)).willReturn(null);
        given(kisTokenCacheLock.tryLock(eq(key), anyString(), any(Duration.class))).willReturn(true);
        given(kisOAuthClient.getApprovalKey("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisApprovalKeyResponse("issued-approval-key"));

        // when
        String result = kisTokenCacheQueryService.getApprovalKey(userId, accountId, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(result).isEqualTo("issued-approval-key");
        verify(valueOperations).set(eq(key), eq("issued-approval-key"), any(Duration.class));
        verify(kisTokenLogCommandService).recordSuccess(accountId, userId, KisTokenType.APPROVAL_KEY);
    }

    @Test
    @DisplayName("접속키 발급이 실패하면 실패 이력을 남기고 예외를 그대로 전파한다")
    void getApprovalKey_kisFails_recordsFailureAndPropagates() {
        // given
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String key = KisTokenCacheKeys.approvalKey(userId);
        KisBusinessException kisException =
                new KisBusinessException(AccountErrorCode.KIS_TOKEN_ISSUE_FAILED, "EGW00001", "발급 실패");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(key)).willReturn(null);
        given(kisTokenCacheLock.tryLock(eq(key), anyString(), any(Duration.class))).willReturn(true);
        given(kisOAuthClient.getApprovalKey("app-key", "secret-key", AccountType.REAL)).willThrow(kisException);

        // when & then
        assertThatThrownBy(() ->
                kisTokenCacheQueryService.getApprovalKey(userId, accountId, "app-key", "secret-key", AccountType.REAL))
                .isSameAs(kisException);

        verify(kisTokenLogCommandService)
                .recordFail(accountId, userId, KisTokenType.APPROVAL_KEY, "EGW00001", "발급 실패");
    }

    @Test
    @DisplayName("접근토큰 캐시에 값이 있으면 그 값을 담은 Optional을 반환한다")
    void peekAccessToken_returnsCachedValue() {
        // given
        UUID userId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(key)).willReturn("cached-token");

        // when
        Optional<String> result = kisTokenCacheQueryService.peekAccessToken(userId);

        // then
        assertThat(result).contains("cached-token");
    }

    @Test
    @DisplayName("접근토큰 캐시가 비어있으면 빈 Optional을 반환한다")
    void peekAccessToken_returnsEmptyWhenCacheMiss() {
        // given
        UUID userId = UUID.randomUUID();
        String key = KisTokenCacheKeys.accessToken(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(key)).willReturn(null);

        // when
        Optional<String> result = kisTokenCacheQueryService.peekAccessToken(userId);

        // then
        assertThat(result).isEmpty();
    }
}
