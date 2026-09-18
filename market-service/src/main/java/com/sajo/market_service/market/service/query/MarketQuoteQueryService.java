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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


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
    private final ConcurrentHashMap<String, CompletableFuture<QuoteResponse>> inFlightRequests = new ConcurrentHashMap<>();

    public QuoteResponse getQuote(UUID userId, String stockCode) {
        long deadline = System.nanoTime() + cacheProperties.lockWaitTimeout().toNanos();
        return getQuoteWithDeadline(userId, stockCode, deadline);
    }

    /**
     * 캐시를 확인하고, MISS면 in-flight 병합 경로로 넘어간다. 팔로워가 대표의 실패/타임아웃 이후 재시도할 때도 이 메서드를 다시 타므로,
     * 그사이 다른 스레드가 이미 캐시를 채웠다면 KIS를 다시 호출하지 않고 그 값을 바로 재사용한다.
     */
    private QuoteResponse getQuoteWithDeadline(UUID userId, String stockCode, long deadline) {
        String cacheKey = createCacheKey(stockCode);
        CacheLookup lookup = findCachedQuote(cacheKey);
        if (isReusableCachedQuote(lookup.quote(), stockCode)) {
            return lookup.quote();
        }
        if (System.nanoTime() >= deadline) {
            throw new BusinessException(MarketErrorCode.QUOTE_CACHE_LOCK_TIMEOUT);
        }

        return getQuoteWithInFlightDedup(userId, stockCode, cacheKey, lookup.redisAvailable(), deadline);
    }

    /**
     * 같은 종목코드에 대한 캐시 MISS가 동시에 여러 건 들어와도, 이 서버 인스턴스 안에서는 실제 조회(Redis 락 획득 + KIS 호출)를 단 하나의 스레드(대표 스레드)만 수행하도록 한다 (single-flight).
     * 나머지 요청은 그 대표 스레드가 만든 {@link CompletableFuture}의 결과를 그대로 공유받아, Redis 락 재시도 폴링({@link #getQuoteWithCacheLock})을 타지 않는다.
     * 인스턴스가 여러 대인 경우의 중복 호출 방지는 기존 Redis 분산 락이 계속 담당하므로, 이 in-flight 병합은 그 위에 한 겹 더 얹는 것이다.
     */
    private QuoteResponse getQuoteWithInFlightDedup(
            UUID userId, String stockCode, String cacheKey, boolean redisAvailable, long deadline) {
        CompletableFuture<QuoteResponse> myFuture = new CompletableFuture<>();
        CompletableFuture<QuoteResponse> existingFuture = inFlightRequests.putIfAbsent(stockCode, myFuture);
        if (existingFuture != null) {
            return joinInFlightFuture(existingFuture, userId, stockCode, deadline);
        }

        try {
            QuoteResponse result = redisAvailable
                    ? getQuoteWithCacheLock(userId, stockCode, cacheKey, deadline)
                    : fetchAndCacheQuote(userId, stockCode, cacheKey);
            if (isValidQuote(result)) {
                myFuture.complete(result);
            } else {
                // 대표 자신은 기존 동작(fetchAndCacheQuote)대로 예외 없이 이 값을 그대로 반환하지만,
                // currentPrice가 없는 "무효 응답"을 팔로워에게 성공으로 그대로 나눠주지는 않는다.
                // 코드 리뷰 반영: 팔로워도 이 경우엔 재시도해서 스스로 유효한 응답을 받도록 한다.
                myFuture.completeExceptionally(
                        new BusinessException(
                                MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID,
                                "KIS 현재가 응답에 currentPrice가 없습니다. stockCode=" + stockCode));
            }
            return result;
        } catch (RuntimeException exception) {
            // 대표 스레드의 실패를 대기 중인 팔로워 전원에게 그대로 퍼뜨리지 않는다.
            // KIS 호출 1건의 일시적 실패(타임아웃 등)가 그 순간 함께 기다리던 요청 수만큼(수십 건) 그대로 증폭되는 것을 막기 위함.
            // 대표 자신은 이 예외를 그대로 던지되, 팔로워는 completeExceptionally로 통보만 받고 각자 재시도한다(joinInFlightFuture 참고).
            myFuture.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlightRequests.remove(stockCode, myFuture);
        }
    }

    private boolean isValidQuote(QuoteResponse quote) {
        return quote != null && quote.currentPrice() != null;
    }

    /**
     * 팔로워는 대표 스레드의 성공(유효한 응답)만 공유받고, 실패나 무효 응답은 공유받지 않는다.
     * 코드 리뷰 반영: 대표의 {@link CompletableFuture}를 무제한 대기(join)하지 않는다 — 대표가
     * user-service/KIS 호출에서 오래 지연되면 팔로워의 서버 스레드까지 그 시간만큼 묶여, 이번 개선이
     * 해결하려던 문제(다수 스레드의 장시간 대기)가 오히려 그대로 재현될 수 있기 때문이다. 남은 시간
     * (deadline, 기존 lockWaitTimeout과 동일한 예산)만큼만 기다리고, 그 안에 끝나지 않거나 대표가
     * 실패/무효 응답을 받으면 {@link #getQuoteWithDeadline}으로 스스로 다시 도전한다. 이렇게 해야
     * 락 기반 방식이 원래 갖고 있던 "한 스레드의 실패/지연이 다른 대기 스레드로 전파되지 않는다"는
     * 장애 격리 특성을 유지한다.
     */
    private QuoteResponse joinInFlightFuture(
            CompletableFuture<QuoteResponse> future, UUID userId, String stockCode, long deadline) {
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new BusinessException(MarketErrorCode.QUOTE_CACHE_LOCK_TIMEOUT);
        }
        try {
            return future.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            log.debug("in-flight 대표 스레드 조회가 남은 시간 안에 끝나지 않아 이 요청은 새로 재시도합니다. stockCode={}", stockCode);
            return retryAfterBackoff(userId, stockCode, deadline);
        } catch (ExecutionException exception) {
            log.debug("in-flight 대표 스레드 조회가 실패(또는 무효 응답)해 이 요청은 새로 재시도합니다. stockCode={}", stockCode);
            return retryAfterBackoff(userId, stockCode, deadline);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(MarketErrorCode.QUOTE_CACHE_LOCK_TIMEOUT);
        }
    }

    /**
     * in-flight 재시도 사이에 기존 Redis 락 재시도(waitForLockRetry)와 동일한 간격(LOCK_RETRY_INTERVAL)의
     * 백오프를 둔다. 코드 리뷰 반영: 대표가 실패/타임아웃을 반복하는 상황(KIS 장애 등)에서 새 대표가
     * 뽑힐 때마다 지연 없이 곧바로 KIS를 다시 호출하면, 오히려 장애 상황에서 호출 빈도가 더 촘촘해질
     * 수 있기 때문이다.
     */
    private QuoteResponse retryAfterBackoff(UUID userId, String stockCode, long deadline) {
        if (!waitForLockRetry()) {
            throw new BusinessException(MarketErrorCode.QUOTE_CACHE_LOCK_TIMEOUT);
        }
        return getQuoteWithDeadline(userId, stockCode, deadline);
    }

    /**
     * 코드 리뷰 반영: 이 메서드는 더 이상 자체적으로 새 deadline을 계산하지 않고, 요청 전체의
     * deadline(팔로워로 대기한 시간까지 포함)을 그대로 물려받는다. 그렇지 않으면 팔로워가 실패 후
     * 새 대표로 승격될 때마다 완전히 새로운 lockWaitTimeout 예산을 다시 받아, 이 스레드의 실제
     * 총 대기 시간이 lockWaitTimeout 하나를 크게 초과할 수 있다.
     */
    private QuoteResponse getQuoteWithCacheLock(UUID userId, String stockCode, String cacheKey, long deadline) {
        //Lock Token 생성
        String lockToken = UUID.randomUUID().toString();

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
