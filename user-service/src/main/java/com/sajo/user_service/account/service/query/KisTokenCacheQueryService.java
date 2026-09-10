package com.sajo.user_service.account.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.cache.KisTokenCacheKeys;
import com.sajo.user_service.account.cache.KisTokenCacheLock;
import com.sajo.user_service.account.cache.KisTokenCacheTtl;
import com.sajo.user_service.account.client.kis.KisOAuthClient;
import com.sajo.user_service.account.client.kis.dto.response.KisAccessTokenResponse;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.domain.KisTokenType;
import com.sajo.user_service.account.exception.AccountErrorCode;
import com.sajo.user_service.account.exception.KisBusinessException;
import com.sajo.user_service.account.service.command.KisTokenLogCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisTokenCacheQueryService {

    private final KisOAuthClient kisOAuthClient;
    private final KisTokenLogCommandService kisTokenLogCommandService;
    private final StringRedisTemplate redisTemplate;
    private final KisTokenCacheLock kisTokenCacheLock;

    // KIS OAuth 호출 최대 시간(connect 3s + read 5s = 8s)보다 여유 있게 - 락 홀더가 죽었을 때 자동 해제되는 상한선
    private static final Duration KIS_LOCK_TTL = Duration.ofSeconds(10);
    // 락을 못 잡은 요청이 대기하다 포기하기까지의 최대 시간
    private static final Duration LOCK_WAIT_TIMEOUT = Duration.ofSeconds(5);
    // 락 재시도 간격
    private static final Duration RETRY_INTERVAL = Duration.ofMillis(50);
    // TODO: KIS 문서상 approval key(웹소켓 접속키) 실제 유효기간 확인 후 조정 - expires_in 같은 응답 필드가 없어 고정값 사용
    private static final Duration APPROVAL_KEY_TTL = Duration.ofHours(24);

    public String getAccessToken(UUID userId, UUID accountId, String appKey, String secretKey, AccountType accountType) {
        String key = KisTokenCacheKeys.accessToken(userId);
        return getTokenWithLock(userId, accountId, KisTokenType.ACCESS_TOKEN, key, () -> {
            KisAccessTokenResponse response = kisOAuthClient.getAccessToken(appKey, secretKey, accountType);
            return new TokenFetchResult(response.access_token(), KisTokenCacheTtl.calculate(response.expires_in()));
        });
    }

    public String getApprovalKey(UUID userId, UUID accountId, String appKey, String secretKey, AccountType accountType) {
        String key = KisTokenCacheKeys.approvalKey(userId);
        return getTokenWithLock(userId, accountId, KisTokenType.APPROVAL_KEY, key, () -> {
            String approvalKey = kisOAuthClient.getApprovalKey(appKey, secretKey, accountType).approval_key();
            return new TokenFetchResult(approvalKey, APPROVAL_KEY_TTL);
        });
    }

    // 캐시에 이미 있는 값만 확인한다 - 캐시 미스여도 KIS를 호출해 새로 발급받지 않는다
    // (계좌 삭제 시 "폐기할 토큰이 있으면 폐기"하려는 용도라, 없는데 새로 발급받아 폐기하는 건 의미가 없음)
    public Optional<String> peekAccessToken(UUID userId) {
        String key = KisTokenCacheKeys.accessToken(userId);
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    // 분산락 + 더블체크 + Redis 장애 시 fail-open을 공통 처리
    // Redis 장애 시에는 kis로 중복 요청 보내질 수 있다
    private String getTokenWithLock(
            UUID userId, UUID accountId, KisTokenType tokenType, String key, Supplier<TokenFetchResult> fetcher
    ) {
        // 1. 캐시 조회
        TokenLookup initialLookup = findCachedToken(key);
        if (initialLookup.token() != null) {
            return initialLookup.token();
        }
        if (!initialLookup.redisAvailable()) {
            return fetchAndCache(userId, accountId, tokenType, key, fetcher);
        }

        // 2. 조회 실패 시 락 획득 후 재확인 후 kis 요청
        String lockToken = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + LOCK_WAIT_TIMEOUT.toNanos();

        while (System.nanoTime() < deadline) {
            boolean locked;
            try {
                locked = kisTokenCacheLock.tryLock(key, lockToken, KIS_LOCK_TTL);
            } catch (DataAccessException e) {
                log.warn("Redis 락 획득 실패해 KIS를 직접 호출합니다. key={}", key, e);
                return fetchAndCache(userId, accountId, tokenType, key, fetcher);
            }

            if (locked) {
                try {
                    TokenLookup recheck = findCachedToken(key); // 재확인
                    if (recheck.token() != null) {
                        return recheck.token();
                    }
                    return fetchAndCache(userId, accountId, tokenType, key, fetcher);
                } finally {
                    releaseLock(key, lockToken);
                }
            }

            TokenLookup waitingLookup = findCachedToken(key);
            if (waitingLookup.token() != null) {
                return waitingLookup.token();
            }
            if (!waitingLookup.redisAvailable()) {
                return fetchAndCache(userId, accountId, tokenType, key, fetcher);
            }
            if (!waitForLockRetry()) {
                break;
            }
        }

        throw new BusinessException(AccountErrorCode.KIS_TOKEN_CACHE_LOCK_TIMEOUT);
    }

    // Redis 조회 결과와 "Redis 자체가 정상이었는지"를 같이 담아서, 캐시 미스(정상)와 장애를 구분한다
    private TokenLookup findCachedToken(String key) {
        try {
            return new TokenLookup(redisTemplate.opsForValue().get(key), true);
        } catch (DataAccessException e) {
            log.warn("Redis 캐시 조회 실패해 KIS를 직접 호출합니다. key={}", key, e);
            return new TokenLookup(null, false);
        }
    }

    // KIS 호출 + 캐시 저장 - 캐시 저장 실패는 삼키고 로그만 남긴다 (발급 자체는 성공했으므로 사용자에게 에러를 낼 이유가 없음)
    private String fetchAndCache(
            UUID userId, UUID accountId, KisTokenType tokenType, String key, Supplier<TokenFetchResult> fetcher
    ) {
        TokenFetchResult result;
        try {
            result = fetcher.get();
        } catch (KisBusinessException e) {
            kisTokenLogCommandService.recordFail(accountId, userId, tokenType, e.getKisErrorCode(), e.getKisMessage());
            throw e;
        }

        kisTokenLogCommandService.recordSuccess(accountId, userId, tokenType);

        if (!result.ttl().isZero()) {
            try {
                redisTemplate.opsForValue().set(key, result.value(), result.ttl());
            } catch (DataAccessException e) {
                log.warn("Redis 캐시 저장 실패했지만 값은 정상 반환합니다. key={}", key, e);
            }
        }

        return result.value();
    }

    private void releaseLock(String key, String lockToken) {
        try {
            kisTokenCacheLock.unlock(key, lockToken);
        } catch (DataAccessException e) {
            log.warn("Redis 락 해제 실패. key={}", key, e);
        }
    }

    private boolean waitForLockRetry() {
        try {
            Thread.sleep(RETRY_INTERVAL.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 인터럽트 상태 복원
            return false;
        }
    }

    private record TokenLookup(String token, boolean redisAvailable) {
    }

    private record TokenFetchResult(String value, Duration ttl) {
    }
}
