package com.sajo.market_service.strategy.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.strategy.controller.dto.response.BacktestDetailResponse;
import com.sajo.market_service.strategy.controller.dto.response.BacktestInternalResponse;
import com.sajo.market_service.strategy.controller.dto.response.BacktestListResponse;
import com.sajo.market_service.strategy.controller.dto.response.BacktestStatusResponse;
import com.sajo.market_service.strategy.domain.Backtest;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import com.sajo.market_service.strategy.repository.query.BacktestQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BacktestQueryService {

    private final BacktestQueryRepository backtestQueryRepository;

    public BacktestStatusResponse getBacktestStatus(
            UUID userId,
            UUID strategyId,
            UUID backtestId
    ) {
        Backtest backtest = getBacktestByOwner(userId, strategyId, backtestId);

        return BacktestStatusResponse.from(backtest);
    }

    public BacktestDetailResponse getBacktestDetail(
            UUID userId,
            UUID strategyId,
            UUID backtestId
    ) {
        Backtest backtest = getBacktestByOwner(userId, strategyId, backtestId);

        return BacktestDetailResponse.from(backtest);
    }

    public BacktestListResponse getBacktests(
            UUID userId,
            UUID strategyId,
            Pageable pageable
    ) {
        Page<Backtest> page = backtestQueryRepository
                .findByStrategyIdAndUserIdAndDeletedAtIsNull(strategyId, userId, pageable);

        return BacktestListResponse.from(page);
    }

    public BacktestInternalResponse getBacktestInternal(UUID backtestId) {
        Backtest backtest = backtestQueryRepository
                .findByIdAndDeletedAtIsNull(backtestId)
                .orElseThrow(() -> new BusinessException(StrategyErrorCode.BACKTEST_NOT_FOUND));

        return BacktestInternalResponse.from(backtest);
    }

    private Backtest getBacktestByOwner(UUID userId, UUID strategyId, UUID backtestId) {
        return backtestQueryRepository
                .findByIdAndStrategyIdAndUserIdAndDeletedAtIsNull(backtestId, strategyId, userId)
                .orElseThrow(() -> new BusinessException(StrategyErrorCode.BACKTEST_NOT_FOUND));
    }
}
