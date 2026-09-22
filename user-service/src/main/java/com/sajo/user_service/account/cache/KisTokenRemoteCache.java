package com.sajo.user_service.account.cache;

import com.sajo.user_service.account.exception.AccountErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

// L2(Redis 공유) 캐시 + 분산락 + 실패마커를 전부 캡슐화한다. Redis 자체 장애(연결 실패 등)는
// RedisUnavailableException으로 바꿔 던져서, 호출자(KisTokenCacheQueryService)가 fail-open 여부를
// 한 곳에서만 판단하면 되게 한다. 반대로 "실패해도 무방한" 보조 작업(저장, 락 해제, 마커)은 여기서
// 직접 로그만 남기고 삼킨다 - 토큰 발급 자체는 이미 성공했으므로 이런 부가 작업 실패로 사용자에게
// 에러를 낼 이유가 없기 때문이다.
@Slf4j
@Component
@RequiredArgsConstructor
public class KisTokenRemoteCache {

    // 락 해제 직후 다음 대기자가 백오프 없이 같은 실패를 바로 반복하는 것을 막는 억제 시간(일시적일 수 있는 일반 실패용)
    private static final Duration RECENT_FAILURE_TTL = Duration.ofSeconds(1);
    // KIS OAuth rate limit(EGW00133, 1분당 1회)은 확정적으로 남은 시간 내내 계속 실패하므로,
    // 일반 실패보다 훨씬 길게 억제해도 손해가 없음 - 1분 윈도우 대비 안전마진
    private static final Duration RATE_LIMIT_RECENT_FAILURE_TTL = Duration.ofSeconds(55);

    private final StringRedisTemplate redisTemplate;
    private final KisTokenCacheLock lock;

    // 캐시 조회 - 없으면 empty, 만료됐으면 empty, Redis 자체 장애면 RedisUnavailableException
    public Optional<KisTokenEntry> get(String key) {
        try {
            String token = redisTemplate.opsForValue().get(key);
            if (token == null) {
                return Optional.empty();
            }
            Long ttlMillis = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
            if (ttlMillis == null || ttlMillis <= 0) {
                return Optional.empty(); // 조회 사이에 만료됨
            }
            return Optional.of(new KisTokenEntry(token, Duration.ofMillis(ttlMillis)));
        } catch (DataAccessException e) {
            throw new RedisUnavailableException(e);
        }
    }

    public void save(String key, KisTokenEntry entry) {
        if (entry.ttl().isZero()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, entry.value(), entry.ttl());
        } catch (DataAccessException e) {
            log.warn("Redis 캐시 저장 실패했지만 값은 정상 반환합니다. key={}", key, e);
        }
    }

    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException e) {
            log.warn("Redis 캐시 삭제 실패. key={}", key, e);
        }
    }

    // 락 획득 시도 자체가 실패(Redis 장애)하면 fail-open 판단이 필요하므로 예외로 전파
    public boolean tryLock(String key, String lockToken) {
        try {
            return lock.tryLock(key, lockToken, KisTokenCacheLock.LOCK_TTL);
        } catch (DataAccessException e) {
            throw new RedisUnavailableException(e);
        }
    }

    public void unlock(String key, String lockToken) {
        try {
            lock.unlock(key, lockToken);
        } catch (DataAccessException e) {
            log.warn("Redis 락 해제 실패. key={}", key, e);
        }
    }

    // 마커에 실패 당시의 AccountErrorCode를 같이 저장해서, fail-fast 시에도 원래 실패 종류(rate limit/자격증명
    // 오류 등)를 그대로 재현할 수 있게 한다 - 저장 실패해도 그냥 넘어감 (억제 실패 = 재시도 허용, 안전한 방향)
    public void markRecentFailure(String key, AccountErrorCode errorCode) {
        Duration ttl = errorCode == AccountErrorCode.KIS_RATE_LIMITED
                ? RATE_LIMIT_RECENT_FAILURE_TTL
                : RECENT_FAILURE_TTL;
        try {
            redisTemplate.opsForValue().set(recentFailureKey(key), errorCode.name(), ttl);
        } catch (DataAccessException e) {
            log.warn("Redis 실패 마커 저장 실패. key={}", key, e);
        }
    }

    // 조회 실패해도 "최근 실패 없음"으로 간주 - Redis 장애 시 이 억제 기능 때문에 KIS 호출 자체가 막히면 안 됨
    public Optional<AccountErrorCode> recentFailure(String key) {
        try {
            String errorCodeName = redisTemplate.opsForValue().get(recentFailureKey(key));
            if (errorCodeName == null) {
                return Optional.empty();
            }
            return Optional.of(AccountErrorCode.valueOf(errorCodeName));
        } catch (DataAccessException e) {
            log.warn("Redis 실패 마커 조회 실패. key={}", key, e);
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            // 마커 값이 유효한 AccountErrorCode 이름이 아닌 경우(손상 등) - 안전하게 "최근 실패 없음"으로 처리
            log.warn("Redis 실패 마커 값이 유효한 에러코드가 아닙니다. key={}", key, e);
            return Optional.empty();
        }
    }

    private String recentFailureKey(String key) {
        return key + ":recent-failure";
    }
}
