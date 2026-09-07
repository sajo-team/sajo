package com.sajo.market_service.strategy.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.strategy.controller.dto.response.StrategyActivationResponse;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import com.sajo.market_service.strategy.repository.command.StrategyCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StrategyActivationCommandService {

    private final StrategyCommandRepository strategyCommandRepository;

    @Transactional
    public StrategyActivationResponse changeActivation(
            UUID userId,
            UUID strategyId,
            Boolean active
    ) {
        // 외부 호출 이후 다시 조회해 최신 상태와 소유권 확인
        Strategy strategy = strategyCommandRepository
                .findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId)
                .orElseThrow(() -> new BusinessException(StrategyErrorCode.STRATEGY_NOT_FOUND));

        if (Boolean.TRUE.equals(active)) {
            strategy.activate();
        } else {
            strategy.deactivate();
        }

        return StrategyActivationResponse.from(strategy);
    }
}
