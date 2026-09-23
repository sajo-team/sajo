package com.sajo.market_service.strategy.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.dto.response.MarketStockPriceResponse;
import com.sajo.market_service.strategy.domain.Backtest;
import com.sajo.market_service.strategy.domain.BacktestStatus;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import com.sajo.market_service.strategy.repository.command.BacktestCommandRepository;
import com.sajo.market_service.strategy.repository.command.StrategyCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * BacktestExecutionService.calculate()(private)를 execute() 경유로 검증한다.
 * DB/Async는 전부 mock/동기 호출로 대체해, 매수·매도 시뮬레이션과 MDD·승률·
 * 연속손실 계산 결과만 순수하게 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class BacktestExecutionServiceTest {

    private static final String STOCK_CODE = "005930";
    private static final Long INITIAL_CASH = 3_000_000L;

    @Mock
    private BacktestCommandRepository backtestCommandRepository;

    @Mock
    private StrategyCommandRepository strategyCommandRepository;

    @Mock
    private BacktestPriceReader backtestPriceReader;

    private BacktestExecutionService backtestExecutionService;
    private Strategy strategy;
    private Backtest backtest;
    private UUID backtestId;

    @BeforeEach
    void setUp() {
        backtestExecutionService = new BacktestExecutionService(
                backtestCommandRepository, strategyCommandRepository, backtestPriceReader
        );

        strategy = Strategy.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                STOCK_CODE,
                "테스트 전략",
                70_000L,
                80_000L,
                new BigDecimal("5.0000"),
                null,
                3_000_000L,
                300_000L,
                null,
                null,
                null
        );
        backtest = Backtest.request(
                strategy, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10), INITIAL_CASH
        );
        backtestId = UUID.randomUUID();

        given(backtestCommandRepository.findById(backtestId)).willReturn(Optional.of(backtest));
        given(strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(any(), any()))
                .willReturn(Optional.of(strategy));
    }

    @Test
    @DisplayName("매수 후 매도 조건을 만족하면 거래 1건과 그에 따른 수익률을 계산한다")
    void completesSingleBuySellCycle() {
        given(backtestPriceReader.read(any(), any(), any())).willReturn(List.of(
                priceOf(1, 65_000L),
                priceOf(2, 85_000L)
        ));

        backtestExecutionService.execute(backtestId);

        assertThat(backtest.getStatus()).isEqualTo(BacktestStatus.COMPLETED);
        assertThat(backtest.getTradeCount()).isEqualTo(1);
        assertThat(backtest.getWinRate()).isEqualByComparingTo("100.0000");
        assertThat(backtest.getMaxConsecutiveLosses()).isEqualTo(0);
        assertThat(backtest.getMdd()).isEqualByComparingTo("0.0000");
        // (4주 * 85,000 - 4주 * 65,000) = 80,000 수익 / 300만원 초기자본
        assertThat(backtest.getTotalReturnRate()).isEqualByComparingTo("2.6700");
    }

    @Test
    @DisplayName("보유 중 평가 자산이 일시적으로 하락하면 MDD가 그 낙폭만큼 음수로 기록된다")
    void tracksMaxDrawdownWhileHolding() {
        given(backtestPriceReader.read(any(), any(), any())).willReturn(List.of(
                priceOf(1, 65_000L), // 매수(4주)
                priceOf(2, 50_000L), // 보유 중 하락 → 낙폭 발생
                priceOf(3, 90_000L)  // 매도
        ));

        backtestExecutionService.execute(backtestId);

        assertThat(backtest.getStatus()).isEqualTo(BacktestStatus.COMPLETED);
        // 2일차 평가자산 2,940,000 vs 그 시점까지의 최고 자산 3,000,000 → -2%
        assertThat(backtest.getMdd()).isEqualByComparingTo("-2.0000");
        assertThat(backtest.getTradeCount()).isEqualTo(1);
        assertThat(backtest.getWinRate()).isEqualByComparingTo("100.0000");
    }

    @Test
    @DisplayName("기간 마지막 날까지 매도 조건을 못 만족하면 보유분은 평가 자산에는 반영되지만 거래 횟수·승률에는 포함되지 않는다")
    void unsoldHoldingOnLastDayIsValuedButNotCountedAsTrade() {
        given(backtestPriceReader.read(any(), any(), any())).willReturn(List.of(
                priceOf(1, 65_000L), // 매수(4주), 매도 조건(80,000) 도달 안 함
                priceOf(2, 72_000L)  // 마지막 날, 여전히 매도 조건 미달
        ));

        backtestExecutionService.execute(backtestId);

        assertThat(backtest.getStatus()).isEqualTo(BacktestStatus.COMPLETED);
        assertThat(backtest.getTradeCount()).isEqualTo(0);
        assertThat(backtest.getWinRate()).isEqualByComparingTo("0.0000");
        // 마지막 종가(72,000)로 평가한 보유분은 수익률 계산에는 반영됨
        assertThat(backtest.getTotalReturnRate()).isEqualByComparingTo("0.9300");
    }

    @Test
    @DisplayName("유효한 종가가 하나도 없으면 백테스트를 실패 처리한다")
    void failsWhenNoValidClosePriceExists() {
        given(backtestPriceReader.read(any(), any(), any())).willReturn(List.of(
                priceOf(1, null),
                priceOf(2, 0L)
        ));

        assertThatThrownBy(() -> backtestExecutionService.execute(backtestId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(StrategyErrorCode.INVALID_STRATEGY)
                );

        assertThat(backtest.getStatus()).isEqualTo(BacktestStatus.FAILED);
    }

    private MarketStockPriceResponse priceOf(int dayOffset, Long closePrice) {
        return new MarketStockPriceResponse(
                LocalDate.of(2026, 1, dayOffset), closePrice, closePrice, closePrice, closePrice, 0L, 0L
        );
    }
}
