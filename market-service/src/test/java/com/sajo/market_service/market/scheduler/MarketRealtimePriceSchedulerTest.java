package com.sajo.market_service.market.scheduler;

import com.sajo.market_service.market.domain.MarketStock;
import com.sajo.market_service.market.domain.MarketStockPrice;
import com.sajo.market_service.market.domain.PriceSource;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.repository.command.MarketStockCommandRepository;
import com.sajo.market_service.market.repository.command.MarketStockPriceCommandRepository;
import com.sajo.market_service.market.websocket.KisWebSocketClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class MarketRealtimePriceSchedulerTest {

    private static final UUID STOCK_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final KisWebSocketClient kisWebSocketClient = mock(KisWebSocketClient.class);
    private final MarketStockCommandRepository marketStockCommandRepository = mock(MarketStockCommandRepository.class);
    private final MarketStockPriceCommandRepository marketStockPriceCommandRepository =
            mock(MarketStockPriceCommandRepository.class);
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, QuoteResponse> quoteRedisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, QuoteResponse> valueOperations = mock(ValueOperations.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-14T15:07:00Z"), ZoneOffset.UTC);

    private final MarketRealtimePriceScheduler scheduler = new MarketRealtimePriceScheduler(
            kisWebSocketClient, marketStockCommandRepository, marketStockPriceCommandRepository,
            quoteRedisTemplate, clock);

    @Test
    void savesOneSnapshotPerSubscribedStockWithFreshRedisQuote() {
        given(kisWebSocketClient.subscribedStockCodes()).willReturn(Set.of("005930"));
        given(quoteRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("market:quote:005930")).willReturn(sampleQuote());
        MarketStock stock = MarketStock.create("005930", "삼성전자", "KOSPI", null, null, null);
        ReflectionTestUtils.setField(stock, "id", STOCK_ID);
        given(marketStockCommandRepository.findByStockCode("005930")).willReturn(Optional.of(stock));

        scheduler.snapshotRealtimePrices();

        ArgumentCaptor<MarketStockPrice> captor = ArgumentCaptor.forClass(MarketStockPrice.class);
        verify(marketStockPriceCommandRepository).save(captor.capture());
        MarketStockPrice saved = captor.getValue();
        assertThat(saved.getStockId()).isEqualTo(STOCK_ID);
        assertThat(saved.getCurrentPrice()).isEqualTo(70000L);
        assertThat(saved.getSource()).isEqualTo(PriceSource.WEBSOCKET);
        assertThat(saved.getTime().getSecond()).isZero();
    }

    @Test
    void doesNothingWhenNoStockIsSubscribed() {
        given(kisWebSocketClient.subscribedStockCodes()).willReturn(Set.of());

        scheduler.snapshotRealtimePrices();

        verifyNoInteractions(quoteRedisTemplate, marketStockPriceCommandRepository);
    }

    @Test
    void skipsStockWhenRedisHasNoCachedQuoteYet() {
        given(kisWebSocketClient.subscribedStockCodes()).willReturn(Set.of("005930"));
        given(quoteRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("market:quote:005930")).willReturn(null);

        scheduler.snapshotRealtimePrices();

        verify(marketStockPriceCommandRepository, never()).save(any());
    }

    @Test
    void skipsStockWhenNotFoundInMarketStockTable() {
        given(kisWebSocketClient.subscribedStockCodes()).willReturn(Set.of("005930"));
        given(quoteRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("market:quote:005930")).willReturn(sampleQuote());
        given(marketStockCommandRepository.findByStockCode("005930")).willReturn(Optional.empty());

        scheduler.snapshotRealtimePrices();

        verify(marketStockPriceCommandRepository, never()).save(any());
    }

    @Test
    void swallowsDuplicateMinuteConstraintViolationWithoutThrowing() {
        given(kisWebSocketClient.subscribedStockCodes()).willReturn(Set.of("005930"));
        given(quoteRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("market:quote:005930")).willReturn(sampleQuote());
        MarketStock stock = MarketStock.create("005930", "삼성전자", "KOSPI", null, null, null);
        ReflectionTestUtils.setField(stock, "id", STOCK_ID);
        given(marketStockCommandRepository.findByStockCode("005930")).willReturn(Optional.of(stock));
        given(marketStockPriceCommandRepository.save(any())).willThrow(new DataIntegrityViolationException("dup"));

        org.assertj.core.api.Assertions.assertThatCode(scheduler::snapshotRealtimePrices)
                .doesNotThrowAnyException();
    }

    private QuoteResponse sampleQuote() {
        return new QuoteResponse(
                "005930", 70000L, 69000L, 70500L, 68800L, 69500L, 500L, new BigDecimal("0.72"),
                123456L, 8610000000L, 4180000L, new BigDecimal("15.20"), new BigDecimal("1.35"),
                new BigDecimal("4605.00"), new BigDecimal("51850.00")
        );
    }
}
