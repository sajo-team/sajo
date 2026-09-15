package com.sajo.market_service.market.service.command;

import com.sajo.market_service.market.domain.MarketStockPrice;
import com.sajo.market_service.market.domain.PriceSource;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.repository.command.MarketStockPriceCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarketRealtimePriceSnapshotCommandServiceTest {

    @Mock
    private MarketStockPriceCommandRepository marketStockPriceCommandRepository;

    private MarketRealtimePriceSnapshotCommandService service;

    private UUID stockId;
    private LocalDate date;
    private LocalTime time;

    @BeforeEach
    void setUp() {
        service = new MarketRealtimePriceSnapshotCommandService(marketStockPriceCommandRepository);
        stockId = UUID.randomUUID();
        date = LocalDate.of(2026, 9, 14);
        time = LocalTime.of(15, 7);
    }

    @Test
    @DisplayName("QuoteResponse 필드를 MarketStockPrice 스냅샷 필드에 맞게 매핑해 저장한다")
    void mapsQuoteResponseFieldsIntoSnapshotAndSaves() {
        QuoteResponse quote = sampleQuote();

        boolean saved = service.saveWebsocketSnapshot(stockId, date, time, quote);

        assertThat(saved).isTrue();
        ArgumentCaptor<MarketStockPrice> captor = ArgumentCaptor.forClass(MarketStockPrice.class);
        verify(marketStockPriceCommandRepository).save(captor.capture());
        MarketStockPrice price = captor.getValue();

        assertThat(price.getStockId()).isEqualTo(stockId);
        assertThat(price.getDate()).isEqualTo(date);
        assertThat(price.getTime()).isEqualTo(time);
        assertThat(price.getCurrentPrice()).isEqualTo(quote.currentPrice());
        assertThat(price.getClosePrice()).isNull();
        assertThat(price.getOpenPrice()).isEqualTo(quote.openPrice());
        assertThat(price.getHighPrice()).isEqualTo(quote.highPrice());
        assertThat(price.getLowPrice()).isEqualTo(quote.lowPrice());
        assertThat(price.getPrevClosePrice()).isEqualTo(quote.previousClosePrice());
        assertThat(price.getChangePrice()).isEqualTo(quote.changePrice());
        assertThat(price.getChangeRate()).isEqualTo(quote.changeRate());
        assertThat(price.getVolume()).isNull();
        assertThat(price.getAccumulatedVolume()).isEqualTo(quote.accumulatedVolume());
        assertThat(price.getAccumulatedTradeAmount()).isEqualTo(quote.tradeAmount());
        assertThat(price.getForeignOwnershipRate()).isNull();
        assertThat(price.getSource()).isEqualTo(PriceSource.WEBSOCKET);
    }

    @Test
    @DisplayName("같은 분에 대한 스냅샷이 이미 존재해 유니크 제약을 위반하면 예외 없이 false를 반환한다")
    void returnsFalseWithoutPropagatingWhenDuplicateSnapshotViolatesUniqueConstraint() {
        QuoteResponse quote = sampleQuote();
        given(marketStockPriceCommandRepository.save(any(MarketStockPrice.class)))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        boolean saved = service.saveWebsocketSnapshot(stockId, date, time, quote);

        assertThat(saved).isFalse();
    }

    private QuoteResponse sampleQuote() {
        return new QuoteResponse(
                "005930",
                69_500L,
                69_000L,
                70_500L,
                68_800L,
                70_000L,
                -500L,
                new BigDecimal("-0.71"),
                123_456L,
                8_610_000_000L,
                4_180_000L,
                new BigDecimal("15.20"),
                new BigDecimal("1.35"),
                new BigDecimal("4605.00"),
                new BigDecimal("51850.00"),
                "150746",
                Instant.parse("2026-09-14T15:07:46Z")
        );
    }
}
