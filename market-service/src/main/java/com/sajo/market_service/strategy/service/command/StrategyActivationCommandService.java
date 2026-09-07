package com.sajo.market_service.strategy.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.strategy.controller.dto.response.StrategyActivationResponse;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import com.sajo.market_service.strategy.repository.command.StrategyCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StrategyActivationCommandService {

    private final StrategyCommandRepository strategyCommandRepository;

    @Transactional
    public StrategyActivationResponse changeActivation(
            UUID userId,
            UUID strategyId,
            Boolean active,
            StrategyActivationSnapshot snapshot
    ) {
        // 외부 호출 이후 다시 조회해 최신 상태와 소유권 확인
        Strategy strategy = strategyCommandRepository
                .findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId)
                .orElseThrow(() -> new BusinessException(StrategyErrorCode.STRATEGY_NOT_FOUND));

        if (Boolean.TRUE.equals(active)) {
            if (snapshot == null || !snapshot.matches(strategy)) {
                log.warn("전략 활성화 실패: 검증 이후 전략 조건이 변경되었습니다. strategyId={}", strategyId);
                throw new BusinessException(
                        StrategyErrorCode.INVALID_STRATEGY,
                        "전략 조건이 변경되어 활성화할 수 없습니다."
                );
            }
            strategy.activate();
        } else {
            strategy.deactivate();
        }

        return StrategyActivationResponse.from(strategy);
    }
}
