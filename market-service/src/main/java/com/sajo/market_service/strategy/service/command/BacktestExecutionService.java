package com.sajo.market_service.strategy.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.dto.response.MarketStockPriceResponse;
import com.sajo.market_service.strategy.domain.Backtest;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import com.sajo.market_service.strategy.repository.command.BacktestCommandRepository;
import com.sajo.market_service.strategy.repository.command.StrategyCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestExecutionService {
    // Backtest 조회 → start() → BacktestPriceReader.read() → 매수·매도 계산
    //→ 수익률·거래 횟수 계산 → complete() → 저장
    private final BacktestCommandRepository backtestCommandRepository;
    private final StrategyCommandRepository strategyCommandRepository;
    private final BacktestPriceReader backtestPriceReader;

    @Async("backtestExecutor")
    public void execute(UUID backtestId) {
        Backtest backtest = backtestCommandRepository.findById(backtestId)
                .orElseThrow(() -> new BusinessException(StrategyErrorCode.BACKTEST_NOT_FOUND));

        try {
            Strategy strategy = strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(
                    backtest.getStrategyId(),
                    backtest.getUserId()
            ).orElseThrow(() -> new BusinessException(StrategyErrorCode.STRATEGY_NOT_FOUND));

            backtest.start();
            backtestCommandRepository.save(backtest);

            List<MarketStockPriceResponse> prices =
                    backtestPriceReader.read(
                            backtest.getStockCode(),
                            backtest.getStartDate(),
                            backtest.getEndDate()
                    );

            if (prices.isEmpty()) {
                throw new BusinessException(StrategyErrorCode.INVALID_STRATEGY, "백테스트 기간의 시세 데이터가 없습니다.");
            }

            BacktestExecutionResult result = calculate(strategy, backtest, prices);

            backtest.complete(
                    result.totalReturnRate(),
                    result.mdd(),
                    result.winRate(),
                    result.tradeCount(),
                    result.maxConsecutiveLosses()
            );

            backtestCommandRepository.save(backtest);

            log.info(
                    "백테스트 실행 완료. backtestId={}, returnRate={}, mdd={}, winRate={}, tradeCount={}, maxConsecutiveLosses={}",
                    backtestId,
                    result.totalReturnRate(),
                    result.mdd(),
                    result.winRate(),
                    result.tradeCount(),
                    result.maxConsecutiveLosses()
            );
        } catch (Exception exception){
            backtest.fail();
            backtestCommandRepository.save(backtest);

            log.warn(
                    "백테스트 실행 실패. backtestId={}",
                    backtestId,
                    exception
            );

            throw exception;
        }
    }

    private BacktestExecutionResult calculate(
            Strategy strategy,
            Backtest backtest,
            List<MarketStockPriceResponse> prices
    ) {
        long cash = backtest.getInitialCash();
        long holdingQuantity = 0L;
        long buyPrice = 0L;
        int tradeCount = 0;
        int winningTradeCount = 0;
        int consecutiveLosses = 0;
        int maxConsecutiveLosses = 0;
        long lastPrice = 0L;
        long peakAsset = backtest.getInitialCash();
        BigDecimal mdd = BigDecimal.ZERO;

        if (strategy.getOrderAmount() == null || strategy.getOrderAmount() <= 0) {
            throw new BusinessException(
                    StrategyErrorCode.INVALID_STRATEGY,
                    "1회 주문 금액이 없어 백테스트를 실행할 수 없습니다."
            );
        }

        for (MarketStockPriceResponse price : prices) {
            if (price.closePrice() == null || price.closePrice() <= 0) continue;

            long currentPrice = price.closePrice();
            lastPrice = currentPrice;

            if (holdingQuantity == 0 && currentPrice <= strategy.getBuyConditionPrice()) {
                long orderAmount = strategy.getOrderAmount();
                long quantity = orderAmount / currentPrice;

                if (quantity > 0 && cash >= quantity * currentPrice) {
                    holdingQuantity = quantity;
                    buyPrice = currentPrice;
                    cash -= quantity * currentPrice;
                }
            }

            if (holdingQuantity > 0 && currentPrice >= strategy.getSellConditionPrice()) {
                cash += holdingQuantity * currentPrice;
                long tradeProfit = (currentPrice - buyPrice) * holdingQuantity;
                holdingQuantity = 0L;
                buyPrice = 0L;
                tradeCount++;

                if (tradeProfit > 0) {
                    winningTradeCount++;
                    consecutiveLosses = 0;
                } else if (tradeProfit < 0) {
                    consecutiveLosses++;
                    maxConsecutiveLosses = Math.max(maxConsecutiveLosses, consecutiveLosses);
                }
            }

            long currentAsset = cash + (holdingQuantity * currentPrice);
            if (currentAsset > peakAsset) {
                peakAsset = currentAsset;
            }

            BigDecimal drawdown = BigDecimal.valueOf(currentAsset - peakAsset)
                    .divide(BigDecimal.valueOf(peakAsset), 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            if (drawdown.compareTo(mdd) < 0) {
                mdd = drawdown;
            }
        }

        if (lastPrice == 0L) {
            throw new BusinessException(StrategyErrorCode.INVALID_STRATEGY, "유효한 종가 데이터가 없습니다.");
        }

        long finalAsset = cash + (holdingQuantity * lastPrice);

        BigDecimal totalReturnRate = BigDecimal.valueOf(finalAsset)
                .subtract(BigDecimal.valueOf(backtest.getInitialCash()))
                .divide(
                        BigDecimal.valueOf(backtest.getInitialCash()),
                        4,
                        RoundingMode.HALF_UP
                )
                .multiply(BigDecimal.valueOf(100));

        BigDecimal winRate = tradeCount == 0
                ? BigDecimal.ZERO.setScale(4)
                : BigDecimal.valueOf(winningTradeCount)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(tradeCount), 4, RoundingMode.HALF_UP);

        return new BacktestExecutionResult(
                totalReturnRate,
                mdd.setScale(4, RoundingMode.HALF_UP),
                winRate,
                tradeCount,
                maxConsecutiveLosses
        );
    }

}
