package com.sajo.user_service.account.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.cache.KisTokenCacheKeys;
import com.sajo.user_service.account.cache.KisTokenCacheLock;
import com.sajo.user_service.account.cache.KisTokenCacheTtl;
import com.sajo.user_service.account.cache.KisTokenEntry;
import com.sajo.user_service.account.cache.KisTokenLocalCache;
import com.sajo.user_service.account.cache.KisTokenRemoteCache;
import com.sajo.user_service.account.cache.RedisUnavailableException;
import com.sajo.user_service.account.client.kis.KisOAuthClient;
import com.sajo.user_service.account.client.kis.dto.response.KisAccessTokenResponse;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.domain.KisTokenType;
import com.sajo.user_service.account.exception.AccountErrorCode;
import com.sajo.user_service.account.exception.KisBusinessException;
import com.sajo.user_service.account.service.command.KisTokenLogCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisTokenCacheQueryService {

    private final KisOAuthClient kisOAuthClient;
    private final KisTokenLogCommandService kisTokenLogCommandService;
    private final KisTokenLocalCache localCache;
    private final KisTokenRemoteCache remoteCache;

    public String getAccessToken(UUID userId, UUID accountId, String appKey, String secretKey, AccountType accountType) {
        String key = KisTokenCacheKeys.accessToken(accountId);
        return localCache.getOrLoad(key, () ->
                loadFromRemoteOrKis(userId, accountId, KisTokenType.ACCESS_TOKEN, key, () -> {
                    KisAccessTokenResponse response = kisOAuthClient.getAccessToken(appKey, secretKey, accountType);
                    return new KisTokenEntry(response.access_token(), KisTokenCacheTtl.calculate(response.expires_in()));
                })
        ).value();
    }

    public String getApprovalKey(UUID userId, UUID accountId, String appKey, String secretKey, AccountType accountType) {
        String key = KisTokenCacheKeys.approvalKey(accountId);
        return localCache.getOrLoad(key, () ->
                loadFromRemoteOrKis(userId, accountId, KisTokenType.APPROVAL_KEY, key, () -> {
                    String approvalKey = kisOAuthClient.getApprovalKey(appKey, secretKey, accountType).approval_key();
                    return new KisTokenEntry(approvalKey, KisTokenCacheTtl.APPROVAL_KEY_TTL);
                })
        ).value();
    }

    // 캐시에 이미 있는 값만 확인한다 - 캐시 미스여도 KIS를 호출해 새로 발급받지 않는다
    // (계좌 삭제 시 "폐기할 토큰이 있으면 폐기"하려는 용도라, 없는데 새로 발급받아 폐기하는 건 의미가 없음)
    public Optional<String> peekAccessToken(UUID accountId) {
        String key = KisTokenCacheKeys.accessToken(accountId);
        Optional<String> local = localCache.peek(key);
        if (local.isPresent()) {
            return local;
        }
        try {
            return remoteCache.get(key).map(KisTokenEntry::value);
        } catch (RedisUnavailableException e) {
            log.warn("계좌 삭제 시 캐시된 토큰 조회 실패. key={}", key, e);
            return Optional.empty();
        }
    }

    // Redis 장애는 여기 딱 한 곳에서만 잡아서 fail-open(KIS 직접 호출) 여부를 판단한다.
    // Redis 장애 시에는 분산락도 같이 무력화되므로 kis로 중복 요청이 나갈 수 있다.
    private KisTokenEntry loadFromRemoteOrKis(
            UUID userId, UUID accountId, KisTokenType tokenType, String key, Supplier<KisTokenEntry> fetcher
    ) {
        try {
            return loadFromRemote(userId, accountId, tokenType, key, fetcher);
        } catch (RedisUnavailableException e) {
            log.warn("Redis 자체 장애로 fail-open, KIS를 직접 호출합니다. key={}", key, e);
            return fetchSaveAndRecord(userId, accountId, tokenType, key, fetcher, () -> {
            });
        }
    }

    // 분산락 + 더블체크. Redis 장애(RedisUnavailableException)는 여기서 잡지 않고 그대로 던져서
    private KisTokenEntry loadFromRemote(
            UUID userId, UUID accountId, KisTokenType tokenType, String key, Supplier<KisTokenEntry> fetcher
    ) {
        Optional<KisTokenEntry> cached = remoteCache.get(key);
        if (cached.isPresent()) {
            return cached.get();
        }

        String lockToken = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + KisTokenCacheLock.WAIT_TIMEOUT.toNanos();

        while (System.nanoTime() < deadline) {
            if (remoteCache.tryLock(key, lockToken)) {
                try {
                    Optional<KisTokenEntry> recheck = remoteCache.get(key); // 재확인
                    if (recheck.isPresent()) {
                        return recheck.get();
                    }
                    Optional<AccountErrorCode> recentFailure = remoteCache.recentFailure(key);
                    if (recentFailure.isPresent()) {
                        // 직전 락 홀더가 방금 실패했다 - 백오프 없이 곧바로 같은 실패를 반복하지 않도록 즉시 실패 처리.
                        throw new BusinessException(recentFailure.get());
                    }

                    // 락이 실제로 보호해야 하는 작업(KIS 호출 + 캐시 저장)이 끝나자마자 바로 해제하고,
                    // 아무도 안 기다리는 이력 기록(DB 쓰기)은 락 해제 이후로 미룬다.
                    return fetchSaveAndRecord(userId, accountId, tokenType, key, fetcher,
                            () -> remoteCache.unlock(key, lockToken));
                } finally {
                    remoteCache.unlock(key, lockToken); // 안전망 - 중복 해제는 무해함
                }
            }

            // 락 획득 못한 경우도 다시 재확인
            Optional<KisTokenEntry> waiting = remoteCache.get(key);
            if (waiting.isPresent()) {
                return waiting.get();
            }
            if (!waitForLockRetry()) {
                break;
            }
        }

        throw new BusinessException(AccountErrorCode.KIS_TOKEN_CACHE_LOCK_TIMEOUT);
    }

    // KIS 호출 + 캐시 저장 + 이력 기록을 공통 처리한다. releaseLock은 "락이 실제로 보호해야 하는 작업이
    // 끝나자마자" 호출되는 콜백이다 - fail-open 경로(애초에 락이 없음)는 no-op을 넘긴다.
    private KisTokenEntry fetchSaveAndRecord(
            UUID userId, UUID accountId, KisTokenType tokenType, String key,
            Supplier<KisTokenEntry> fetcher, Runnable releaseLock
    ) {
        KisTokenEntry result;
        try {
            result = fetcher.get();
        } catch (KisBusinessException e) {
            remoteCache.markRecentFailure(key, (AccountErrorCode) e.getErrorCode());
            releaseLock.run();
            kisTokenLogCommandService.recordFail(accountId, userId, tokenType, e.getKisErrorCode(), e.getKisMessage());
            throw e;
        }

        remoteCache.save(key, result);
        releaseLock.run();
        kisTokenLogCommandService.recordSuccess(accountId, userId, tokenType);

        return result;
    }

    private boolean waitForLockRetry() {
        try {
            Thread.sleep(KisTokenCacheLock.RETRY_INTERVAL.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 인터럽트 상태 복원
            return false;
        }
    }
}
