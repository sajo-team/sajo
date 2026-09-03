package com.sajo.market_service.strategy.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.strategy.controller.dto.request.StrategyCreateRequest;
import com.sajo.market_service.strategy.controller.dto.request.StrategyUpdateRequest;
import com.sajo.market_service.strategy.controller.dto.response.StrategyCreateResponse;
import com.sajo.market_service.strategy.controller.dto.response.StrategyUpdateResponse;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import com.sajo.market_service.strategy.repository.command.StrategyCommandRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StrategyCommandService {
    private final StrategyCommandRepository strategyCommandRepository;

    @Transactional
    public StrategyCreateResponse createStrategy(
            UUID userId,
            StrategyCreateRequest request
    ) {
        Strategy strategy = Strategy.create(
                userId,
                request.stockId(),
                request.stockCode(),
                request.strategyName(),
                request.buyConditionPrice(),
                request.sellConditionPrice(),
                request.stopLossRate(),
                request.targetReturnRate(),
                request.allocatedAmount(),
                request.perCondition(),
                request.pbrCondition(),
                request.roeCondition()
        );

        Strategy savedStrategy = strategyCommandRepository.save(strategy);

        return StrategyCreateResponse.from(savedStrategy);
    }

    @Transactional
    public StrategyUpdateResponse updateStrategy(
            UUID userId,
            UUID strategyId,
            @Valid StrategyUpdateRequest request
    ) {
        Strategy strategy = strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId)
                .orElseThrow(() -> new BusinessException(StrategyErrorCode.STRATEGY_NOT_FOUND));

        strategy.update(
                request.strategyName(),
                request.buyConditionPrice(),
                request.sellConditionPrice(),
                request.stopLossRate(),
                request.targetReturnRate(),
                request.allocatedAmount(),
                request.perCondition(),
                request.pbrCondition(),
                request.roeCondition()
        );

        return StrategyUpdateResponse.from(strategy);
    }

    @Transactional
    public void deleteStrategy(UUID userId, UUID strategyId) {
        Strategy strategy = strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId)
                .orElseThrow(() -> new BusinessException(StrategyErrorCode.STRATEGY_NOT_FOUND));

        strategy.softDelete(userId);
    }
}
