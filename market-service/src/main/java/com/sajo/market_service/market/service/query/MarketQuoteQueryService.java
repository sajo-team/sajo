package com.sajo.market_service.market.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.cache.MarketQuoteCacheKey;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class MarketQuoteQueryService {

    private static final Duration LOCK_RETRY_INTERVAL = Duration.ofMillis(50);
    private static final String PREVIOUS_CLOSE_PRICE_ABSENT_MARKER_VALUE = "1";

    private final RedisTemplate<String, QuoteResponse> quoteRedisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final MarketQuoteCacheLock marketQuoteCacheLock;
    private final UserAccountFeignClient userAccountFeignClient;
    private final KisApiClient kisApiClient;
    private final MarketQuoteCacheProperties cacheProperties;

    public QuoteResponse getQuote(UUID userId, String stockCode) {
        String cacheKey = createCacheKey(stockCode);
        CacheLookup initialLookup = findCachedQuote(cacheKey);
        if (isReusableCachedQuote(initialLookup.quote(), stockCode)) {
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
                        if (isReusableCachedQuote(lockAcquiredLookup.quote(), stockCode)) {
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
            if (isReusableCachedQuote(waitingLookup.quote(), stockCode)) {
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
        if (quote == null || quote.currentPrice() == null) {
            return quote;
        }
        try {
            quoteRedisTemplate.opsForValue().set(cacheKey, quote, cacheProperties.ttl());
            if (quote.previousClosePrice() == null) {
                // KIS REST 응답 자체에 previousClosePrice가 없는 종목(예: 상장 첫날처럼 전일 종가가
                // 원천적으로 없는 경우, 코드 리뷰 반영). 매 호출마다 KIS를 다시 부르지 않도록 짧은
                // TTL(previousClosePriceMissingTtl) 동안만 "KIS로 직접 확인했지만 값이 없었다"는
                // 사실을 기억해둔다. 이 마커가 있는 동안은 캐시에 previousClosePrice가 없어도 재사용
                // 가능한 것으로 취급하고(isReusableCachedQuote), 마커가 만료되면 다음 호출에서 다시
                // KIS로 확인한다(값이 새로 생겼을 수 있으므로).
                markPreviousClosePriceConfirmedAbsent(stockCode);
            }
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
        return MarketQuoteCacheKey.of(stockCode);
    }

    /**
     * 캐시 HIT을 그대로 반환해도 되는지 판단한다.
     *
     * currentPrice와 previousClosePrice가 둘 다 있으면 바로 재사용한다.
     * previousClosePrice만 없는 경우는 두 가지로 나뉜다:
     * <ul>
     *   <li>WebSocket 경로({@code MarketRealtimePriceUpdateService})가 REST로 한 번도 조회된 적
     *   없는 종목의 캐시를 currentPrice만 채운 채로 먼저 만든 경우 — 아직 KIS로 확인된 적이 없으므로
     *   "캐시 MISS"로 취급해 KIS REST 재조회를 강제한다. Trading의 주문 전 상·하한가 검증에
     *   previousClosePrice가 필요하기 때문이다.</li>
     *   <li>이미 REST로 확인했지만 KIS 응답 자체에 previousClosePrice가 없었던 경우(상장 첫날 등) —
     *   {@link #markPreviousClosePriceConfirmedAbsent}가 남긴 마커가 previousClosePriceMissingTtl 동안 살아있으면 그 확인 결과를 신뢰하고 그대로 재사용한다.
     *   이 구분이 없으면 그런 종목은 캐싱이 무력화되어 매 호출마다 KIS를 직접 호출하게 된다.</li>
     * </ul>
     */
    private boolean isReusableCachedQuote(QuoteResponse quote, String stockCode) {
        if (quote == null || quote.currentPrice() == null) {
            return false;
        }
        if (quote.previousClosePrice() != null) {
            return true;
        }
        return isPreviousClosePriceConfirmedAbsent(stockCode);
    }

    private void markPreviousClosePriceConfirmedAbsent(String stockCode) {
        try {
            stringRedisTemplate.opsForValue().set(
                    MarketQuoteCacheKey.noPreviousClosePriceMarkerKey(stockCode),
                    PREVIOUS_CLOSE_PRICE_ABSENT_MARKER_VALUE,
                    cacheProperties.previousClosePriceMissingTtl()
            );
        } catch (DataAccessException exception) {
            log.warn("previousClosePrice 부재 확인 마커 저장에 실패했습니다. stockCode={}", stockCode, exception);
        }
    }

    private boolean isPreviousClosePriceConfirmedAbsent(String stockCode) {
        try {
            return Boolean.TRUE.equals(
                    stringRedisTemplate.hasKey(MarketQuoteCacheKey.noPreviousClosePriceMarkerKey(stockCode)));
        } catch (DataAccessException exception) {
            log.warn("previousClosePrice 부재 확인 마커 조회에 실패했습니다. stockCode={}", stockCode, exception);
            return false;
        }
    }

    private record CacheLookup(QuoteResponse quote, boolean redisAvailable) {
    }
}
