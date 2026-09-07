package com.sajo.market_service.strategy.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.strategy.controller.dto.request.BacktestCreateRequest;
import com.sajo.market_service.strategy.controller.dto.response.BacktestCreateResponse;
import com.sajo.market_service.strategy.domain.Backtest;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import com.sajo.market_service.strategy.repository.command.BacktestCommandRepository;
import com.sajo.market_service.strategy.repository.command.StrategyCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BacktestCommandService {

    private final StrategyCommandRepository strategyCommandRepository;
    private final BacktestCommandRepository backtestCommandRepository;

    @Transactional
    public BacktestCreateResponse createBacktest(
            UUID userId,
            UUID strategyId,
            BacktestCreateRequest request
    ) {
        Strategy strategy = strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId)
                .orElseThrow(() -> new BusinessException(StrategyErrorCode.STRATEGY_NOT_FOUND));

        Backtest backtest = Backtest.request(
                strategy,
                request.startDate(),
                request.endDate(),
                request.initialCash()
        );

        Backtest savedBacktest = backtestCommandRepository.save(backtest);

        return BacktestCreateResponse.from(savedBacktest);
    }
}
