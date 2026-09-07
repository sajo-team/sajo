package com.sajo.market_service.strategy.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.controller.dto.response.InternalStockIndicatorResponse;
import com.sajo.market_service.market.controller.dto.response.InternalStockQuoteResponse;
import com.sajo.market_service.market.service.query.MarketInternalQueryService;
import com.sajo.market_service.strategy.controller.dto.request.StrategyActivationRequest;
import com.sajo.market_service.strategy.controller.dto.request.StrategyCreateRequest;
import com.sajo.market_service.strategy.controller.dto.request.StrategyUpdateRequest;
import com.sajo.market_service.strategy.controller.dto.response.StrategyActivationResponse;
import com.sajo.market_service.strategy.controller.dto.response.StrategyCreateResponse;
import com.sajo.market_service.strategy.controller.dto.response.StrategyUpdateResponse;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import com.sajo.market_service.strategy.repository.command.StrategyCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StrategyCommandService {

    private final StrategyCommandRepository strategyCommandRepository;
    private final MarketInternalQueryService marketInternalQueryService;
    private final StrategyActivationCommandService strategyActivationCommandService;

    @Transactional
    public StrategyCreateResponse createStrategy(
            UUID userId,
            StrategyCreateRequest request
    ) {
        log.info("전략 생성 요청 시작. stockCode={}", request.stockCode());

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

        log.info("전략 생성 완료. strategyId={}, stockCode={}, status={}",
                savedStrategy.getId(), savedStrategy.getStockCode(), savedStrategy.getStatus());

        return StrategyCreateResponse.from(savedStrategy);
    }

    @Transactional
    public StrategyUpdateResponse updateStrategy(
            UUID userId,
            UUID strategyId,
            StrategyUpdateRequest request
    ) {
        log.info("전략 수정 요청 시작. strategyId={}", strategyId);

        Strategy strategy = strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId)
                .orElseThrow(() -> {
                    log.warn("전략 수정 실패: 전략을 찾을 수 없습니다. strategyId={}", strategyId);
                    return new BusinessException(StrategyErrorCode.STRATEGY_NOT_FOUND);
                });

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

        log.info("전략 수정 완료. strategyId={}, status={}", strategyId, strategy.getStatus());

        return StrategyUpdateResponse.from(strategy);
    }

    @Transactional
    public void deleteStrategy(UUID userId, UUID strategyId) {
        log.info("전략 삭제 요청 시작. strategyId={}", strategyId);

        Strategy strategy = strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId)
                .orElseThrow(() -> {
                    log.warn("전략 삭제 실패: 전략을 찾을 수 없습니다. strategyId={}", strategyId);
                    return new BusinessException(StrategyErrorCode.STRATEGY_NOT_FOUND);
                });

        strategy.delete(userId);
        log.info("전략 삭제 완료. strategyId={}", strategyId);
    }

    // Market 내부 API를 통해 현재가 및 전략에 설정된 PER/PBR 조건 검증, ROE 지표 보류
    public StrategyActivationResponse updateActivation(
            UUID userId,
            UUID strategyId,
            StrategyActivationRequest request
    ) {
        log.info("전략 상태 변경 요청 시작. strategyId={}, active={}", strategyId, request.active());

        Strategy strategy = strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId)
                .orElseThrow(() -> {
                    log.warn("전략 상태 변경 실패: 전략을 찾을 수 없습니다. strategyId={}", strategyId);
                    return new BusinessException(StrategyErrorCode.STRATEGY_NOT_FOUND);
                });

        if (Boolean.TRUE.equals(request.active())) {
            log.info("전략 활성화 전 Market 데이터 검증 시작. strategyId={}, stockCode={}",
                    strategyId, strategy.getStockCode());
            validateMarketDataAvailable(userId, strategy);
        }

        // 별도 Bean의 @Transactional 메서드 호출
        return strategyActivationCommandService.changeActivation(
                userId,
                strategyId,
                request.active()
        );
    }

    private void validateMarketDataAvailable(UUID userId, Strategy strategy) {
        InternalStockQuoteResponse quote =
                marketInternalQueryService.getQuote(userId, strategy.getStockCode());

        log.info("Market 현재가 조회 완료. stockCode={}, currentPrice={}, baseTime={}",
                strategy.getStockCode(),
                quote == null ? null : quote.currentPrice(),
                quote == null ? null : quote.baseTime());

        if (quote == null || quote.currentPrice() == null || quote.currentPrice() <= 0) {
            log.warn("전략 활성화 실패: 유효한 현재가가 없습니다. strategyId={}, stockCode={}",
                    strategy.getId(), strategy.getStockCode());
            throw new BusinessException(
                    StrategyErrorCode.INVALID_STRATEGY,
                    "현재가 정보가 없어 전략을 활성화할 수 없습니다."
            );
        }

        if (!hasIndicatorCondition(strategy)) {
            log.info("전략에 PER/PBR 조건이 없어 투자지표 조회를 생략합니다. strategyId={}", strategy.getId());
            return;
        }

        InternalStockIndicatorResponse indicator =
                marketInternalQueryService.getIndicator(strategy.getStockCode());

        if (indicator == null) {
            log.warn("전략 활성화 실패: 투자지표 응답이 없습니다. stockCode={}",
                    strategy.getStockCode());
            throw new BusinessException(
                    StrategyErrorCode.INVALID_STRATEGY,
                    "투자지표 정보가 없어 전략을 활성화할 수 없습니다."
            );
        }

        log.info("Market 투자지표 조회 완료. stockCode={}, per={}, pbr={}, referenceDate={}",
                strategy.getStockCode(), indicator.per(), indicator.pbr(), indicator.referenceDate());

        validateRequiredIndicator(strategy.getPerCondition(), indicator.per(), "PER");
        validateRequiredIndicator(strategy.getPbrCondition(), indicator.pbr(), "PBR");
        log.info("전략 활성화용 Market 데이터 검증 완료. stockCode={}", strategy.getStockCode());
    }

    private boolean hasIndicatorCondition(Strategy strategy) {
        return strategy.getPerCondition() != null
                || strategy.getPbrCondition() != null;
    }

    private void validateRequiredIndicator(
            BigDecimal condition,
            BigDecimal actual,
            String indicatorName
    ) {
        if (condition != null && actual == null) {
            log.warn("전략 활성화 실패: {} 지표가 없습니다.", indicatorName);
            throw new BusinessException(
                    StrategyErrorCode.INVALID_STRATEGY,
                    indicatorName + " 지표가 없어 전략을 활성화할 수 없습니다."
            );
        }
    }
}
