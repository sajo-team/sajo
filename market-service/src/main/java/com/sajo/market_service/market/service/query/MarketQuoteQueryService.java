package com.sajo.market_service.market.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.cache.MarketQuoteCacheLock;
import com.sajo.market_service.market.client.kis.KisApiClient;
import com.sajo.market_service.market.client.user.UserAccountFeignClient;
import com.sajo.market_service.market.client.user.dto.UserKisTokenResponse;
import com.sajo.market_service.market.config.MarketQuoteCacheProperties;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketQuoteQueryService {

    private static final String QUOTE_CACHE_KEY_PREFIX = "market:quote:";
    private static final Duration LOCK_RETRY_INTERVAL = Duration.ofMillis(50);

    private final RedisTemplate<String, QuoteResponse> quoteRedisTemplate;
    private final MarketQuoteCacheLock marketQuoteCacheLock;
    private final UserAccountFeignClient userAccountFeignClient;
    private final KisApiClient kisApiClient;
    private final MarketQuoteCacheProperties cacheProperties;

    @Transactional(readOnly = true)
    public QuoteResponse getQuote(UUID userId, String stockCode) {
        String cacheKey = createCacheKey(stockCode);
        CacheLookup initialLookup = findCachedQuote(cacheKey);
        if (initialLookup.quote() != null) {
            return initialLookup.quote();
        }
        if (!initialLookup.redisAvailable()) {
            return fetchAndCacheQuote(userId, stockCode, cacheKey);
        }

        return getQuoteWithCacheLock(userId, stockCode, cacheKey);
    }

    private QuoteResponse getQuoteWithCacheLock(UUID userId, String stockCode, String cacheKey) {
        //Lock Token 생성
        String lockToken = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + cacheProperties.lockWaitTimeout().toNanos();

        while (System.nanoTime() < deadline) {
            try {
                //stockCode,Lock Token, Lock TTL
                if (marketQuoteCacheLock.tryLock(stockCode, lockToken, cacheProperties.lockTtl())) {
                    try {
                        CacheLookup lockAcquiredLookup = findCachedQuote(cacheKey);
                        if (lockAcquiredLookup.quote() != null) {
                            return lockAcquiredLookup.quote();
                        }
                        return fetchAndCacheQuote(userId, stockCode, cacheKey);
                    } finally {
                        //Lock 해제
                        releaseLock(stockCode, lockToken);
                    }
                }
            } catch (DataAccessException exception) {
                log.warn("Redis lock 획득에 실패해 KIS 현재가를 직접 조회합니다. stockCode={}", stockCode, exception);
                return fetchAndCacheQuote(userId, stockCode, cacheKey);
            }

            CacheLookup waitingLookup = findCachedQuote(cacheKey);
            if (waitingLookup.quote() != null) {
                return waitingLookup.quote();
            }
            if (!waitingLookup.redisAvailable()) {
                return fetchAndCacheQuote(userId, stockCode, cacheKey);
            }
            if (!waitForLockRetry()) {
                break;
            }
        }

        throw new BusinessException(MarketErrorCode.QUOTE_CACHE_LOCK_TIMEOUT);
    }

    /**
     * Redis 저장에 실패해도 예외를 던지지 않고 KIS 결과를 반환
     *
     * @param userId
     * @param stockCode
     * @param cacheKey
     * @return
     */
    private QuoteResponse fetchAndCacheQuote(UUID userId, String stockCode, String cacheKey) {
        UserKisTokenResponse credentials = userAccountFeignClient.getKisToken(userId);
        QuoteResponse quote = kisApiClient.getQuote(credentials, stockCode);
        try {
            quoteRedisTemplate.opsForValue().set(cacheKey, quote, cacheProperties.ttl());
        } catch (DataAccessException exception) {
            log.warn("Redis 캐시 저장에 실패했지만 KIS 현재가를 반환합니다. stockCode={}", stockCode, exception);
        }
        return quote;
    }

    private CacheLookup findCachedQuote(String cacheKey) {
        try {
            return new CacheLookup(quoteRedisTemplate.opsForValue().get(cacheKey), true);
        } catch (DataAccessException exception) {
            log.warn("Redis 캐시 조회에 실패해 KIS 현재가를 직접 조회합니다. cacheKey={}", cacheKey, exception);
            return new CacheLookup(null, false);
        }
    }

    private void releaseLock(String stockCode, String lockToken) {
        try {
            marketQuoteCacheLock.unlock(stockCode, lockToken);
        } catch (DataAccessException exception) {
            log.warn("Redis lock 해제에 실패했습니다. stockCode={}", stockCode, exception);
        }
    }

    private boolean waitForLockRetry() {
        try {
            Thread.sleep(LOCK_RETRY_INTERVAL);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String createCacheKey(String stockCode) {
        return QUOTE_CACHE_KEY_PREFIX + stockCode;
    }

    private record CacheLookup(QuoteResponse quote, boolean redisAvailable) {
    }
}
