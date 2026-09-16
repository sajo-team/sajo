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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestCommandService {

    private final StrategyCommandRepository strategyCommandRepository;
    private final BacktestCommandRepository backtestCommandRepository;
    private final BacktestExecutionService backtestExecutionService;

    public BacktestCreateResponse createBacktest(
            UUID userId,
            UUID strategyId,
            BacktestCreateRequest request
    ) {
        log.info("백테스트 생성 요청 시작. strategyId={}, startDate={}, endDate={}",
                strategyId, request.startDate(), request.endDate());

        Strategy strategy = strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId)
                .orElseThrow(() -> {
                    log.warn("백테스트 생성 실패: 전략을 찾을 수 없습니다. strategyId={}", strategyId);
                    return new BusinessException(StrategyErrorCode.STRATEGY_NOT_FOUND);
                });

        Backtest backtest = Backtest.request(
                strategy,
                request.startDate(),
                request.endDate(),
                request.initialCash()
        );

        Backtest savedBacktest = backtestCommandRepository.saveAndFlush(backtest);

        backtestExecutionService.execute(savedBacktest.getId());

        log.info("백테스트 생성 완료. backtestId={}, strategyId={}, status={}",
                savedBacktest.getId(), strategyId, savedBacktest.getStatus());

        return BacktestCreateResponse.from(savedBacktest);
    }
}
