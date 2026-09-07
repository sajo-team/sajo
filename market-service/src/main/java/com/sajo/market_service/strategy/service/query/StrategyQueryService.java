package com.sajo.market_service.strategy.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.strategy.controller.dto.response.StrategyDetailResponse;
import com.sajo.market_service.strategy.controller.dto.response.StrategyInternalResponse;
import com.sajo.market_service.strategy.controller.dto.response.StrategyListResponse;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.domain.StrategyStatus;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import com.sajo.market_service.strategy.repository.query.StrategyQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class StrategyQueryService {

    private final StrategyQueryRepository strategyQueryRepository;

    public StrategyListResponse getStrategies(
            UUID userId,
            StrategyStatus status,
            String stockCode,
            Pageable pageable
    ) {
        log.info("전략 목록 조회 시작. status={}, stockCode={}, page={}, size={}",
                status, stockCode, pageable.getPageNumber(), pageable.getPageSize());

        Page<Strategy> page = strategyQueryRepository.findStrategies(
                userId,
                status,
                stockCode,
                pageable
        );

        log.info("전략 목록 조회 완료. resultCount={}, totalElements={}",
                page.getNumberOfElements(), page.getTotalElements());

        return StrategyListResponse.from(page);
    }

    public StrategyDetailResponse getStrategy(UUID userId, UUID strategyId) {
        log.info("전략 상세 조회 시작. strategyId={}", strategyId);

        Strategy strategy = strategyQueryRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId)
                .orElseThrow(() -> {
                    log.warn("전략 상세 조회 실패: 전략을 찾을 수 없습니다. strategyId={}", strategyId);
                    return new BusinessException(StrategyErrorCode.STRATEGY_NOT_FOUND);
                });

        log.info("전략 상세 조회 완료. strategyId={}, status={}", strategyId, strategy.getStatus());

        return StrategyDetailResponse.from(strategy);
    }

    public StrategyInternalResponse getStrategyInternal(UUID strategyId) {
        log.info("내부 전략 조회 시작. strategyId={}", strategyId);

        Strategy strategy = strategyQueryRepository.findByIdAndDeletedAtIsNull(strategyId)
                .orElseThrow(() -> {
                    log.warn("내부 전략 조회 실패: 전략을 찾을 수 없습니다. strategyId={}", strategyId);
                    return new BusinessException(StrategyErrorCode.STRATEGY_NOT_FOUND);
                });

        log.info("내부 전략 조회 완료. strategyId={}, userId={}, status={}",
                strategyId, strategy.getUserId(), strategy.getStatus());

        return StrategyInternalResponse.from(strategy);
    }
}
