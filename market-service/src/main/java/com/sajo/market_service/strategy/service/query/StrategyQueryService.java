package com.sajo.market_service.strategy.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.strategy.controller.dto.response.StrategyDetailResponse;
import com.sajo.market_service.strategy.controller.dto.response.StrategyListResponse;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.domain.StrategyStatus;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import com.sajo.market_service.strategy.repository.query.StrategyQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StrategyQueryService {

    private final StrategyQueryRepository strategyQueryRepository;

    public StrategyListResponse getStrategies(
            UUID userId,
            StrategyStatus status,
            String stockCode,
            Pageable pageable
    ) {
        Page<Strategy> page = strategyQueryRepository.findStrategies(
                userId,
                status,
                stockCode,
                pageable
        );

        return StrategyListResponse.from(page);
    }

    public StrategyDetailResponse getStrategy(UUID userId, UUID strategyId) {
        Strategy strategy = strategyQueryRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId)
                .orElseThrow(() -> new BusinessException(StrategyErrorCode.STRATEGY_NOT_FOUND));

        return StrategyDetailResponse.from(strategy);
    }
}
