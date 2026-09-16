package com.sajo.market_service.market.service.command;

import com.sajo.market_service.market.cache.MarketQuoteCacheLock;
import com.sajo.market_service.market.config.MarketQuoteCacheProperties;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.kafka.dto.MarketPriceUpdatedEvent;
import com.sajo.market_service.market.kafka.producer.MarketPriceEventProducer;
import com.sajo.market_service.market.service.parser.KisRealtimePriceMessageParser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class MarketRealtimePriceUpdateServiceTest {

    private static final String RAW_SINGLE_RECORD =
            "0|H0STCNT0|001|005930^150746^249250^5^-10250^-3.95^250742.47^249500^254500^248500^249500^249000^1"
                    + "^14871538^3728926343750^206240^176545^-29695^81.34^7912656^6435830^5^0.44^106.69^090011^5"
                    + "^-250^113225^5^-5250^145726^2^750^20260914^20^N^85729^141952^343655^1371668^0.25^12052370"
                    + "^123.39^0^^249500^2";

    private final KisRealtimePriceMessageParser parser = new KisRealtimePriceMessageParser();
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, QuoteResponse> quoteRedisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, QuoteResponse> valueOperations = mock(ValueOperations.class);
    private final MarketQuoteCacheProperties cacheProperties =
            new MarketQuoteCacheProperties(Duration.ofSeconds(60), null, null, null);
    private final MarketQuoteCacheLock cacheLock = mock(MarketQuoteCacheLock.class);
    private final MarketPriceEventProducer priceEventProducer = mock(MarketPriceEventProducer.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-14T15:07:46Z"), ZoneOffset.UTC);

    private final MarketRealtimePriceUpdateService service = new MarketRealtimePriceUpdateService(
            parser, quoteRedisTemplate, cacheProperties, cacheLock, priceEventProducer, clock);

    @Test
    void updatesRedisCacheWithNormalizedQuoteAndConfiguredTtl() {
        given(cacheLock.tryLock(eq("005930"), anyString(), any(Duration.class))).willReturn(true);
        given(quoteRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("market:quote:005930")).willReturn(null);

        service.updateFromRawMessage(RAW_SINGLE_RECORD);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QuoteResponse> captor = ArgumentCaptor.forClass(QuoteResponse.class);
        verify(valueOperations).set(eq("market:quote:005930"), captor.capture(), eq(Duration.ofSeconds(60)));
        QuoteResponse saved = captor.getValue();
        assertThat(saved.stockCode()).isEqualTo("005930");
        assertThat(saved.currentPrice()).isEqualTo(249250L);
        assertThat(saved.fetchedAt()).isEqualTo(Instant.parse("2026-09-14T15:07:46Z"));
        verify(cacheLock).unlock(eq("005930"), anyString());
    }

    @Test
    void publishesMarketPriceUpdatedEventAfterRedisIsUpdated() {
        given(cacheLock.tryLock(eq("005930"), anyString(), any(Duration.class))).willReturn(true);
        given(quoteRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("market:quote:005930")).willReturn(null);

        service.updateFromRawMessage(RAW_SINGLE_RECORD);

        ArgumentCaptor<MarketPriceUpdatedEvent> captor = ArgumentCaptor.forClass(MarketPriceUpdatedEvent.class);
        verify(priceEventProducer).publish(captor.capture());
        MarketPriceUpdatedEvent event = captor.getValue();
        assertThat(event.payload().stockCode()).isEqualTo("005930");
        assertThat(event.payload().currentPrice()).isEqualTo(249250L);
        // 원문의 businessDate(20260914)+tradeTime(150746, Asia/Seoul)로부터 계산된 실제 체결 시각.
        assertThat(event.payload().tradedAt()).isEqualTo(Instant.parse("2026-09-14T06:07:46Z"));
    }

    @Test
    void doesNotPropagateWhenKafkaPublishFails() {
        given(cacheLock.tryLock(eq("005930"), anyString(), any(Duration.class))).willReturn(true);
        given(quoteRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("market:quote:005930")).willReturn(null);
        doThrow(new IllegalStateException("Kafka unavailable"))
                .when(priceEventProducer).publish(any(MarketPriceUpdatedEvent.class));

        service.updateFromRawMessage(RAW_SINGLE_RECORD);

        // Kafka 발행 실패가 예외로 전파되지 않고, 이미 끝난 Redis 반영도 그대로 유지된다.
        verify(valueOperations).set(eq("market:quote:005930"), any(QuoteResponse.class), eq(Duration.ofSeconds(60)));
        verify(cacheLock).unlock(eq("005930"), anyString());
    }

    @Test
    void publishesEventEvenWhenLockReleaseFailsAfterSuccessfulRedisUpdate() {
        // Redis set()은 성공했지만 unlock()에서 일시적인 DataAccessException이 나는 좁은 타이밍
        // (코드 리뷰 반영, #239) — 락은 TTL로 자동 해제되므로 unlock 실패가 "이미 끝난 갱신"의
        // Kafka 이벤트 발행까지 막아서는 안 된다.
        given(cacheLock.tryLock(eq("005930"), anyString(), any(Duration.class))).willReturn(true);
        given(quoteRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("market:quote:005930")).willReturn(null);
        doThrow(new QueryTimeoutException("unlock timeout"))
                .when(cacheLock).unlock(eq("005930"), anyString());

        service.updateFromRawMessage(RAW_SINGLE_RECORD);

        verify(valueOperations).set(eq("market:quote:005930"), any(QuoteResponse.class), eq(Duration.ofSeconds(60)));
        verify(priceEventProducer).publish(any(MarketPriceUpdatedEvent.class));
    }

    @Test
    void doesNotPropagateWhenRedisReadFails() {
        given(cacheLock.tryLock(eq("005930"), anyString(), any(Duration.class))).willReturn(true);
        given(quoteRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willThrow(new QueryTimeoutException("timeout"));

        service.updateFromRawMessage(RAW_SINGLE_RECORD);

        verify(valueOperations, never()).set(anyString(), any(QuoteResponse.class), any(Duration.class));
        // 락은 실패 시에도 반드시 해제되어야 한다.
        verify(cacheLock).unlock(eq("005930"), anyString());
        // Redis 반영 자체가 실패했으므로 Kafka 이벤트도 발행되지 않는다.
        verify(priceEventProducer, never()).publish(any(MarketPriceUpdatedEvent.class));
    }

    @Test
    void ignoresNonTradeFramesWithoutTouchingRedis() {
        service.updateFromRawMessage("{\"header\":{\"tr_id\":\"H0STCNT0\"}}");

        verifyNoInteractions(quoteRedisTemplate);
        verifyNoInteractions(cacheLock);
    }

    @Test
    void skipsCacheUpdateWhenRestPathAlreadyHoldsTheLock() {
        given(cacheLock.tryLock(eq("005930"), anyString(), any(Duration.class))).willReturn(false);

        service.updateFromRawMessage(RAW_SINGLE_RECORD);

        verifyNoInteractions(quoteRedisTemplate);
        verify(cacheLock, never()).unlock(anyString(), anyString());
    }
}
