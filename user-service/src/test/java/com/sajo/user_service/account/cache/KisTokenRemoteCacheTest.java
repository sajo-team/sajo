package com.sajo.user_service.account.cache;

import com.sajo.user_service.account.exception.AccountErrorCode;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// KisTokenCacheQueryService가 KisTokenRemoteCache를 Mock으로 대체하면서, L2(Redis) 자체의 세부
// 동작(특히 rate-limit 억제 TTL 분기)을 검증하던 기존 테스트가 사라졌었다 - 리뷰로 지적받아 이 클래스
// 전용 순수 단위 테스트로 옮겨 복원한다.
@ExtendWith(MockitoExtension.class)
class KisTokenRemoteCacheTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private KisTokenCacheLock lock;

    private KisTokenRemoteCache remoteCache;

    @BeforeEach
    void setUp() {
        remoteCache = new KisTokenRemoteCache(redisTemplate, lock);
    }

    @Test
    @DisplayName("KIS_RATE_LIMITED로 실패하면, 확정적으로 한동안 계속 실패할 것이므로 훨씬 긴 시간(55초) 동안 억제한다")
    void markRecentFailure_rateLimited_usesLongerTtl() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        // when
        remoteCache.markRecentFailure("key", AccountErrorCode.KIS_RATE_LIMITED);

        // then
        verify(valueOperations).set("key:recent-failure", "KIS_RATE_LIMITED", Duration.ofSeconds(55));
    }

    @Test
    @DisplayName("rate limit이 아닌 일반 실패는 짧은 억제 시간(1초)을 쓴다 - 일시적일 수 있으니 재시도 기회를 오래 막지 않음")
    void markRecentFailure_notRateLimited_usesShortTtl() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        // when
        remoteCache.markRecentFailure("key", AccountErrorCode.INVALID_KIS_CREDENTIALS);

        // then
        verify(valueOperations).set("key:recent-failure", "INVALID_KIS_CREDENTIALS", Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("마커 저장 중 Redis 장애가 나도 예외를 던지지 않고 조용히 넘어간다")
    void markRecentFailure_redisFails_doesNotThrow() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        willThrow(new RedisConnectionFailureException("연결 실패"))
                .given(valueOperations).set(anyString(), anyString(), any(Duration.class));

        // when & then - 억제 저장 실패는 "재시도 허용"이라는 안전한 방향이라 예외를 삼킨다
        remoteCache.markRecentFailure("key", AccountErrorCode.KIS_RATE_LIMITED);
    }

    @Test
    @DisplayName("최근 실패 마커가 있으면 그 에러코드를 담은 Optional을 반환한다")
    void recentFailure_markerExists_returnsErrorCode() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("key:recent-failure")).willReturn("KIS_RATE_LIMITED");

        // when
        Optional<AccountErrorCode> result = remoteCache.recentFailure("key");

        // then
        assertThat(result).contains(AccountErrorCode.KIS_RATE_LIMITED);
    }

    @Test
    @DisplayName("최근 실패 마커가 없으면 빈 Optional을 반환한다")
    void recentFailure_noMarker_returnsEmpty() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("key:recent-failure")).willReturn(null);

        // when
        Optional<AccountErrorCode> result = remoteCache.recentFailure("key");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("마커 조회 중 Redis 장애가 나면 억제 기능 때문에 KIS 호출이 막히면 안 되므로 빈 Optional로 처리한다")
    void recentFailure_redisFails_returnsEmpty() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("key:recent-failure")).willThrow(new RedisConnectionFailureException("연결 실패"));

        // when
        Optional<AccountErrorCode> result = remoteCache.recentFailure("key");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("마커 값이 유효한 에러코드가 아니면(손상 등) 안전하게 최근 실패 없음으로 처리한다")
    void recentFailure_corruptedMarker_returnsEmpty() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("key:recent-failure")).willReturn("NOT_A_REAL_ERROR_CODE");

        // when
        Optional<AccountErrorCode> result = remoteCache.recentFailure("key");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("캐시에 값이 있고 만료 전이면 남은 TTL과 함께 반환한다")
    void get_hit_returnsEntryWithRemainingTtl() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("key")).willReturn("token");
        given(redisTemplate.getExpire("key", TimeUnit.MILLISECONDS)).willReturn(60_000L);

        // when
        Optional<KisTokenEntry> result = remoteCache.get("key");

        // then
        assertThat(result).contains(new KisTokenEntry("token", Duration.ofMillis(60_000L)));
    }

    @Test
    @DisplayName("캐시 미스면 빈 Optional을 반환한다")
    void get_miss_returnsEmpty() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("key")).willReturn(null);

        // when
        Optional<KisTokenEntry> result = remoteCache.get("key");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("조회와 TTL 확인 사이에 만료되면(TTL 0 이하) 빈 Optional로 취급한다")
    void get_expiredBetweenLookups_returnsEmpty() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("key")).willReturn("token");
        given(redisTemplate.getExpire("key", TimeUnit.MILLISECONDS)).willReturn(0L);

        // when
        Optional<KisTokenEntry> result = remoteCache.get("key");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("조회 중 Redis 장애가 나면 RedisUnavailableException을 던진다")
    void get_redisFails_throwsRedisUnavailableException() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("key")).willThrow(new RedisConnectionFailureException("연결 실패"));

        // when & then
        assertThatThrownBy(() -> remoteCache.get("key")).isInstanceOf(RedisUnavailableException.class);
    }

    @Test
    @DisplayName("TTL이 0이면 저장을 건너뛴다")
    void save_zeroTtl_skipsSaving() {
        // when
        remoteCache.save("key", new KisTokenEntry("token", Duration.ZERO));

        // then
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("저장 중 Redis 장애가 나도 예외를 던지지 않는다 - 발급 자체는 이미 성공했으므로")
    void save_redisFails_doesNotThrow() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        willThrow(new RedisConnectionFailureException("연결 실패"))
                .given(valueOperations).set(eq("key"), eq("token"), any(Duration.class));

        // when & then
        remoteCache.save("key", new KisTokenEntry("token", Duration.ofHours(1)));
    }

    @Test
    @DisplayName("락 획득 시도 중 Redis 장애가 나면 RedisUnavailableException을 던진다")
    void tryLock_redisFails_throwsRedisUnavailableException() {
        // given
        willThrow(new RedisConnectionFailureException("연결 실패"))
                .given(lock).tryLock(anyString(), anyString(), any(Duration.class));

        // when & then
        assertThatThrownBy(() -> remoteCache.tryLock("key", "token"))
                .isInstanceOf(RedisUnavailableException.class);
    }

    @Test
    @DisplayName("락 해제 중 Redis 장애가 나도 예외를 던지지 않는다")
    void unlock_redisFails_doesNotThrow() {
        // given
        willThrow(new RedisConnectionFailureException("연결 실패"))
                .given(lock).unlock(anyString(), anyString());

        // when & then
        remoteCache.unlock("key", "token");
    }
}
