package com.sajo.market_service.market.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.cache.MarketQuoteCacheLock;
import com.sajo.market_service.market.client.kis.KisApiClient;
import com.sajo.market_service.market.client.user.UserAccountFeignClient;
import com.sajo.market_service.market.client.user.dto.UserKisTokenResponse;
import com.sajo.market_service.market.config.MarketQuoteCacheProperties;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarketQuoteQueryServiceTest {

    private static final String STOCK_CODE = "005930";
    private static final String CACHE_KEY = "market:quote:005930";
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration LOCK_WAIT_TIMEOUT = Duration.ofSeconds(2);

    @Mock
    private RedisTemplate<String, QuoteResponse> quoteRedisTemplate;

    @Mock
    private ValueOperations<String, QuoteResponse> valueOperations;

    @Mock
    private MarketQuoteCacheLock marketQuoteCacheLock;

    @Mock
    private UserAccountFeignClient userAccountFeignClient;

    @Mock
    private KisApiClient kisApiClient;

    private MarketQuoteQueryService marketQuoteQueryService;

    @BeforeEach
    void setUp() {
        marketQuoteQueryService = new MarketQuoteQueryService(
                quoteRedisTemplate,
                marketQuoteCacheLock,
                userAccountFeignClient,
                kisApiClient,
                new MarketQuoteCacheProperties(CACHE_TTL, LOCK_TTL, LOCK_WAIT_TIMEOUT)
        );
        given(quoteRedisTemplate.opsForValue()).willReturn(valueOperations);
        lenient().when(marketQuoteCacheLock.tryLock(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    @Test
    @DisplayName("캐시 HIT면 KIS를 호출하지 않고 현재가를 반환한다")
    void returnsCachedQuoteWithoutCallingKis() {
        UUID userId = UUID.randomUUID();
        QuoteResponse cachedQuote = quoteResponse(70_000L);
        given(valueOperations.get(CACHE_KEY)).willReturn(cachedQuote);

        QuoteResponse response = marketQuoteQueryService.getQuote(userId, STOCK_CODE);

        assertThat(response).isEqualTo(cachedQuote);
        verify(valueOperations).get(CACHE_KEY);
        verify(userAccountFeignClient, never()).getKisToken(userId);
        verify(kisApiClient, never()).getQuote(any(), anyString());
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("캐시 MISS면 KIS 현재가를 조회하고 60초 TTL로 저장한다")
    void fetchesFromKisAndCachesQuoteOnMiss() {
        UUID userId = UUID.randomUUID();
        UserKisTokenResponse credentials = new UserKisTokenResponse("token", "app-key", "secret-key");
        QuoteResponse fetchedQuote = quoteResponse(70_000L);
        given(valueOperations.get(CACHE_KEY)).willReturn(null);
        given(userAccountFeignClient.getKisToken(userId)).willReturn(credentials);
        given(kisApiClient.getQuote(credentials, STOCK_CODE)).willReturn(fetchedQuote);

        QuoteResponse response = marketQuoteQueryService.getQuote(userId, STOCK_CODE);

        assertThat(response).isEqualTo(fetchedQuote);
        verify(kisApiClient).getQuote(credentials, STOCK_CODE);
        verify(valueOperations).set(CACHE_KEY, fetchedQuote, CACHE_TTL);
    }

    @Test
    @DisplayName("KIS 조회가 실패하면 캐시에 데이터를 저장하지 않는다")
    void doesNotCacheWhenKisLookupFails() {
        UUID userId = UUID.randomUUID();
        UserKisTokenResponse credentials = new UserKisTokenResponse("token", "app-key", "secret-key");
        BusinessException exception = new BusinessException(
                MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID,
                "KIS 현재가 조회에 실패했습니다."
        );
        given(valueOperations.get(CACHE_KEY)).willReturn(null);
        given(userAccountFeignClient.getKisToken(userId)).willReturn(credentials);
        given(kisApiClient.getQuote(credentials, STOCK_CODE)).willThrow(exception);

        assertThatThrownBy(() -> marketQuoteQueryService.getQuote(userId, STOCK_CODE))
                .isSameAs(exception);

        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("Redis 조회에 실패해도 KIS 현재가를 직접 조회해 반환한다")
    void returnsKisQuoteWhenRedisGetFails() {
        UUID userId = UUID.randomUUID();
        UserKisTokenResponse credentials = new UserKisTokenResponse("token", "app-key", "secret-key");
        QuoteResponse fetchedQuote = quoteResponse(70_000L);
        given(valueOperations.get(CACHE_KEY))
                .willThrow(new RedisConnectionFailureException("Redis unavailable"));
        given(userAccountFeignClient.getKisToken(userId)).willReturn(credentials);
        given(kisApiClient.getQuote(credentials, STOCK_CODE)).willReturn(fetchedQuote);

        QuoteResponse response = marketQuoteQueryService.getQuote(userId, STOCK_CODE);

        assertThat(response).isEqualTo(fetchedQuote);
        verify(kisApiClient).getQuote(credentials, STOCK_CODE);
    }

    @Test
    @DisplayName("Redis 저장에 실패해도 KIS 현재가를 정상 반환한다")
    void returnsKisQuoteWhenRedisSetFails() {
        UUID userId = UUID.randomUUID();
        UserKisTokenResponse credentials = new UserKisTokenResponse("token", "app-key", "secret-key");
        QuoteResponse fetchedQuote = quoteResponse(70_000L);
        given(valueOperations.get(CACHE_KEY)).willReturn(null);
        given(marketQuoteCacheLock.tryLock(eq(STOCK_CODE), anyString(), any(Duration.class))).willReturn(true);
        given(userAccountFeignClient.getKisToken(userId)).willReturn(credentials);
        given(kisApiClient.getQuote(credentials, STOCK_CODE)).willReturn(fetchedQuote);
        doThrow(new RedisConnectionFailureException("Redis unavailable"))
                .when(valueOperations).set(CACHE_KEY, fetchedQuote, CACHE_TTL);

        QuoteResponse response = marketQuoteQueryService.getQuote(userId, STOCK_CODE);

        assertThat(response).isEqualTo(fetchedQuote);
        verify(marketQuoteCacheLock).unlock(eq(STOCK_CODE), anyString());
    }

    @Test
    @DisplayName("Redis lock 획득이 실패해도 KIS 현재가를 직접 조회해 반환한다")
    void returnsKisQuoteWhenRedisLockAcquisitionFails() {
        UUID userId = UUID.randomUUID();
        UserKisTokenResponse credentials = new UserKisTokenResponse("token", "app-key", "secret-key");
        QuoteResponse fetchedQuote = quoteResponse(70_000L);
        given(valueOperations.get(CACHE_KEY)).willReturn(null);
        given(marketQuoteCacheLock.tryLock(eq(STOCK_CODE), anyString(), any(Duration.class)))
                .willThrow(new RedisConnectionFailureException("Redis unavailable"));
        given(userAccountFeignClient.getKisToken(userId)).willReturn(credentials);
        given(kisApiClient.getQuote(credentials, STOCK_CODE)).willReturn(fetchedQuote);
        doThrow(new RedisConnectionFailureException("Redis unavailable"))
                .when(valueOperations).set(CACHE_KEY, fetchedQuote, CACHE_TTL);

        QuoteResponse response = marketQuoteQueryService.getQuote(userId, STOCK_CODE);

        assertThat(response).isEqualTo(fetchedQuote);
        verify(kisApiClient).getQuote(credentials, STOCK_CODE);
        verify(marketQuoteCacheLock, never()).unlock(eq(STOCK_CODE), anyString());
    }

    @Test
    @DisplayName("동일 종목의 동시 캐시 MISS는 한 번만 KIS를 호출한다")
    void preventsDuplicateKisCallsForSameStockCode() throws Exception {
        Map<String, QuoteResponse> cache = new ConcurrentHashMap<>();
        Map<String, String> locks = new ConcurrentHashMap<>();
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UserKisTokenResponse credentials = new UserKisTokenResponse("token", "app-key", "secret-key");
        QuoteResponse fetchedQuote = quoteResponse(70_000L);
        CountDownLatch kisStarted = new CountDownLatch(1);
        CountDownLatch allowKisResponse = new CountDownLatch(1);
        AtomicInteger kisCallCount = new AtomicInteger();

        given(valueOperations.get(anyString())).willAnswer(invocation -> cache.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            cache.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), any(QuoteResponse.class), any(Duration.class));
        given(marketQuoteCacheLock.tryLock(anyString(), anyString(), any(Duration.class)))
                .willAnswer(invocation -> locks.putIfAbsent(
                        invocation.getArgument(0), invocation.getArgument(1)
                ) == null);
        doAnswer(invocation -> {
            locks.remove(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(marketQuoteCacheLock).unlock(anyString(), anyString());
        given(userAccountFeignClient.getKisToken(any(UUID.class))).willReturn(credentials);
        given(kisApiClient.getQuote(credentials, STOCK_CODE)).willAnswer(invocation -> {
            kisCallCount.incrementAndGet();
            kisStarted.countDown();
            assertThat(allowKisResponse.await(1, TimeUnit.SECONDS)).isTrue();
            return fetchedQuote;
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<QuoteResponse> first = executor.submit(() -> marketQuoteQueryService.getQuote(firstUserId, STOCK_CODE));
            assertThat(kisStarted.await(1, TimeUnit.SECONDS)).isTrue();
            Future<QuoteResponse> second = executor.submit(() -> marketQuoteQueryService.getQuote(secondUserId, STOCK_CODE));

            allowKisResponse.countDown();

            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo(fetchedQuote);
            assertThat(second.get(2, TimeUnit.SECONDS)).isEqualTo(fetchedQuote);
            assertThat(kisCallCount).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("서로 다른 종목의 동시 캐시 MISS는 서로 블로킹하지 않는다")
    void doesNotBlockDifferentStockCodes() throws Exception {
        String otherStockCode = "000660";
        Map<String, QuoteResponse> cache = new ConcurrentHashMap<>();
        Map<String, String> locks = new ConcurrentHashMap<>();
        UserKisTokenResponse credentials = new UserKisTokenResponse("token", "app-key", "secret-key");
        CountDownLatch bothKisCallsStarted = new CountDownLatch(2);

        given(valueOperations.get(anyString())).willAnswer(invocation -> cache.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            cache.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), any(QuoteResponse.class), any(Duration.class));
        given(marketQuoteCacheLock.tryLock(anyString(), anyString(), any(Duration.class)))
                .willAnswer(invocation -> locks.putIfAbsent(
                        invocation.getArgument(0), invocation.getArgument(1)
                ) == null);
        doAnswer(invocation -> {
            locks.remove(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(marketQuoteCacheLock).unlock(anyString(), anyString());
        given(userAccountFeignClient.getKisToken(any(UUID.class))).willReturn(credentials);
        given(kisApiClient.getQuote(eq(credentials), anyString())).willAnswer(invocation -> {
            bothKisCallsStarted.countDown();
            assertThat(bothKisCallsStarted.await(1, TimeUnit.SECONDS)).isTrue();
            return new QuoteResponse(
                    invocation.getArgument(1), 70_000L, null, null, null, null,
                    null, null, null, null, null, null, null, null, null
            );
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<QuoteResponse> first = executor.submit(() -> marketQuoteQueryService.getQuote(UUID.randomUUID(), STOCK_CODE));
            Future<QuoteResponse> second = executor.submit(() -> marketQuoteQueryService.getQuote(UUID.randomUUID(), otherStockCode));

            assertThat(first.get(2, TimeUnit.SECONDS).stockCode()).isEqualTo(STOCK_CODE);
            assertThat(second.get(2, TimeUnit.SECONDS).stockCode()).isEqualTo(otherStockCode);
            verify(kisApiClient, times(2)).getQuote(eq(credentials), anyString());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("lock 획득 timeout이면 KIS 중복 호출 없이 도메인 예외를 반환한다")
    void throwsBusinessExceptionWhenCacheLockTimesOut() {
        MarketQuoteQueryService shortTimeoutService = new MarketQuoteQueryService(
                quoteRedisTemplate,
                marketQuoteCacheLock,
                userAccountFeignClient,
                kisApiClient,
                new MarketQuoteCacheProperties(CACHE_TTL, LOCK_TTL, Duration.ofMillis(10))
        );
        given(valueOperations.get(CACHE_KEY)).willReturn(null);
        given(marketQuoteCacheLock.tryLock(eq(STOCK_CODE), anyString(), any(Duration.class))).willReturn(false);

        assertThatThrownBy(() -> shortTimeoutService.getQuote(UUID.randomUUID(), STOCK_CODE))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(MarketErrorCode.QUOTE_CACHE_LOCK_TIMEOUT));

        verify(kisApiClient, never()).getQuote(any(), anyString());
    }

    private QuoteResponse quoteResponse(long currentPrice) {
        return new QuoteResponse(
                STOCK_CODE, currentPrice, 69_000L, 70_500L, 68_800L, 69_500L,
                500L, null, 123_456L, 8_610_000_000L, 4_180_000L,
                null, null, null, null
        );
    }
}
