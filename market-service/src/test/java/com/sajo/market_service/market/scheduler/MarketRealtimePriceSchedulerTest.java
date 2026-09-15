package com.sajo.market_service.market.scheduler;

import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.repository.query.MarketStockCollectionTarget;
import com.sajo.market_service.market.repository.query.MarketStockQueryRepository;
import com.sajo.market_service.market.service.command.MarketRealtimePriceSnapshotCommandService;
import com.sajo.market_service.market.websocket.KisWebSocketClient;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class MarketRealtimePriceSchedulerTest {

    private static final UUID STOCK_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final KisWebSocketClient kisWebSocketClient = mock(KisWebSocketClient.class);
    private final MarketStockQueryRepository marketStockQueryRepository = mock(MarketStockQueryRepository.class);
    private final MarketRealtimePriceSnapshotCommandService snapshotCommandService =
            mock(MarketRealtimePriceSnapshotCommandService.class);
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, QuoteResponse> quoteRedisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, QuoteResponse> valueOperations = mock(ValueOperations.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-14T15:07:00Z"), ZoneOffset.UTC);

    private final MarketRealtimePriceScheduler scheduler = new MarketRealtimePriceScheduler(
            kisWebSocketClient, marketStockQueryRepository, snapshotCommandService,
            quoteRedisTemplate, clock);

    @Test
    void savesOneSnapshotPerSubscribedStockWithFreshRedisQuote() {
        given(kisWebSocketClient.subscribedStockCodes()).willReturn(Set.of("005930"));
        given(quoteRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("market:quote:005930")).willReturn(sampleQuote());
        given(marketStockQueryRepository.findCollectionTargetsByStockCodes(Set.of("005930")))
                .willReturn(List.of(target("005930", STOCK_ID)));
        given(snapshotCommandService.saveWebsocketSnapshot(eq(STOCK_ID), any(), any(), any()))
                .willReturn(true);

        scheduler.snapshotRealtimePrices();

        verify(snapshotCommandService).saveWebsocketSnapshot(
                STOCK_ID, LocalDate.of(2026, 9, 14), LocalTime.of(15, 7), sampleQuote());
    }

    @Test
    void doesNothingWhenNoStockIsSubscribed() {
        given(kisWebSocketClient.subscribedStockCodes()).willReturn(Set.of());

        scheduler.snapshotRealtimePrices();

        verifyNoInteractions(quoteRedisTemplate, marketStockQueryRepository, snapshotCommandService);
    }

    @Test
    void skipsStockWhenRedisHasNoCachedQuoteYet() {
        given(kisWebSocketClient.subscribedStockCodes()).willReturn(Set.of("005930"));
        given(quoteRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("market:quote:005930")).willReturn(null);
        given(marketStockQueryRepository.findCollectionTargetsByStockCodes(Set.of("005930")))
                .willReturn(List.of(target("005930", STOCK_ID)));

        scheduler.snapshotRealtimePrices();

        verifyNoInteractions(snapshotCommandService);
    }

    @Test
    void skipsStockWhenNotFoundInMarketStockTable() {
        given(kisWebSocketClient.subscribedStockCodes()).willReturn(Set.of("005930"));
        given(quoteRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("market:quote:005930")).willReturn(sampleQuote());
        given(marketStockQueryRepository.findCollectionTargetsByStockCodes(Set.of("005930")))
                .willReturn(List.of());

        scheduler.snapshotRealtimePrices();

        verifyNoInteractions(snapshotCommandService);
    }

    @Test
    void swallowsDuplicateMinuteConstraintViolationWithoutThrowing() {
        given(kisWebSocketClient.subscribedStockCodes()).willReturn(Set.of("005930"));
        given(quoteRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("market:quote:005930")).willReturn(sampleQuote());
        given(marketStockQueryRepository.findCollectionTargetsByStockCodes(Set.of("005930")))
                .willReturn(List.of(target("005930", STOCK_ID)));
        given(snapshotCommandService.saveWebsocketSnapshot(eq(STOCK_ID), any(), any(), any()))
                .willReturn(false);

        org.assertj.core.api.Assertions.assertThatCode(scheduler::snapshotRealtimePrices)
                .doesNotThrowAnyException();
    }

    @Test
    void skipsWholeTickWithoutThrowingWhenBatchStockLookupFails() {
        given(kisWebSocketClient.subscribedStockCodes()).willReturn(Set.of("005930", "000660"));
        given(marketStockQueryRepository.findCollectionTargetsByStockCodes(Set.of("005930", "000660")))
                .willThrow(new QueryTimeoutException("timeout"));

        org.assertj.core.api.Assertions.assertThatCode(scheduler::snapshotRealtimePrices)
                .doesNotThrowAnyException();

        verifyNoInteractions(quoteRedisTemplate, snapshotCommandService);
    }

    private MarketStockCollectionTarget target(String stockCode, UUID stockId) {
        return new MarketStockCollectionTarget() {
            public UUID getStockId() { return stockId; }
            public String getStockCode() { return stockCode; }
        };
    }

    private QuoteResponse sampleQuote() {
        return new QuoteResponse(
                "005930", 70000L, 69000L, 70500L, 68800L, 69500L, 500L, new BigDecimal("0.72"),
                123456L, 8610000000L, 4180000L, new BigDecimal("15.20"), new BigDecimal("1.35"),
                new BigDecimal("4605.00"), new BigDecimal("51850.00")
        );
    }
}
