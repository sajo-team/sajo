package com.sajo.market_service.strategy.domain;

import com.sajo.common.entity.BaseUpdatableEntity;
import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "p_strategies")
public class Strategy extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "stock_id", nullable = false)
    private UUID stockId;

    @Column(name = "stock_code", nullable = false, length = 6)
    private String stockCode;

    @Column(name = "strategy_name", nullable = false, length = 100)
    private String strategyName;

    @Column(name = "buy_condition_price", nullable = false)
    private Long buyConditionPrice;

    @Column(name = "sell_condition_price", nullable = false)
    private Long sellConditionPrice;

    @Column(name = "stop_loss_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal stopLossRate;

    @Column(name = "target_return_rate", precision = 10, scale = 4)
    private BigDecimal targetReturnRate;

    @Column(name = "allocated_amount", nullable = false)
    private Long allocatedAmount;

    @Column(name = "order_amount")
    private Long orderAmount;

    @Column(name = "per_condition", precision = 10, scale = 4)
    private BigDecimal perCondition;

    @Column(name = "pbr_condition", precision = 10, scale = 4)
    private BigDecimal pbrCondition;

    @Column(name = "roe_condition", precision = 10, scale = 4)
    private BigDecimal roeCondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StrategyStatus status;

    @Column(name = "activated_at")
    private Instant activatedAt;

    private Strategy(
            UUID userId,
            UUID stockId,
            String stockCode,
            String strategyName,
            Long buyConditionPrice,
            Long sellConditionPrice,
            BigDecimal stopLossRate,
            BigDecimal targetReturnRate,
            Long allocatedAmount,
            BigDecimal perCondition,
            BigDecimal pbrCondition,
            BigDecimal roeCondition
    ) {
        this.userId = userId;
        this.stockId = stockId;
        this.stockCode = stockCode;
        this.strategyName = strategyName;
        this.buyConditionPrice = buyConditionPrice;
        this.sellConditionPrice = sellConditionPrice;
        this.stopLossRate = stopLossRate;
        this.targetReturnRate = targetReturnRate;
        this.allocatedAmount = allocatedAmount;
        this.orderAmount = null;
        this.perCondition = perCondition;
        this.pbrCondition = pbrCondition;
        this.roeCondition = roeCondition;
        this.status = StrategyStatus.INACTIVE;
    }

    public static Strategy create(
            UUID userId,
            UUID stockId,
            String stockCode,
            String strategyName,
            Long buyConditionPrice,
            Long sellConditionPrice,
            BigDecimal stopLossRate,
            BigDecimal targetReturnRate,
            Long allocatedAmount,
            BigDecimal perCondition,
            BigDecimal pbrCondition,
            BigDecimal roeCondition
    ) {
        if (userId == null) {
            throw new BusinessException(StrategyErrorCode.INVALID_STRATEGY, "사용자 ID는 필수입니다.");
        }

        if (stockId == null) {
            throw new BusinessException(StrategyErrorCode.INVALID_STRATEGY, "종목 ID는 필수입니다.");
        }

        if (stockCode == null || stockCode.isBlank()) {
            throw new BusinessException(StrategyErrorCode.INVALID_STRATEGY, "종목 코드는 필수입니다.");
        }

        if (strategyName == null || strategyName.isBlank()) {
            throw new BusinessException(StrategyErrorCode.INVALID_STRATEGY, "전략명은 필수입니다.");
        }

        if (buyConditionPrice == null || buyConditionPrice <= 0) {
            throw new BusinessException(StrategyErrorCode.INVALID_STRATEGY, "매수 조건 가격은 0보다 커야 합니다.");
        }

        if (sellConditionPrice == null || sellConditionPrice <= 0) {
            throw new BusinessException(StrategyErrorCode.INVALID_STRATEGY, "매도 조건 가격은 0보다 커야 합니다.");
        }

        if (stopLossRate == null || stopLossRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(StrategyErrorCode.INVALID_STRATEGY, "손절률은 0보다 커야 합니다.");
        }

        if (allocatedAmount == null || allocatedAmount <= 0) {
            throw new BusinessException(StrategyErrorCode.INVALID_STRATEGY, "전략 배정 금액은 0보다 커야 합니다.");
        }

        return new Strategy(
                userId,
                stockId,
                stockCode,
                strategyName,
                buyConditionPrice,
                sellConditionPrice,
                stopLossRate,
                targetReturnRate,
                allocatedAmount,
                perCondition,
                pbrCondition,
                roeCondition
        );
    }

    public void update(
            String strategyName,
            Long buyConditionPrice,
            Long sellConditionPrice,
            BigDecimal stopLossRate,
            BigDecimal targetReturnRate,
            Long allocatedAmount,
            BigDecimal perCondition,
            BigDecimal pbrCondition,
            BigDecimal roeCondition
    ) {
        if (strategyName != null) {
            if (strategyName.isBlank()) {
                throw new BusinessException(StrategyErrorCode.INVALID_STRATEGY);
            }
            this.strategyName = strategyName;
        }

        if (buyConditionPrice != null) {
            validatePositive(buyConditionPrice, "매수 조건 가격은 0보다 커야 합니다.");
            this.buyConditionPrice = buyConditionPrice;
        }

        if (sellConditionPrice != null) {
            validatePositive(sellConditionPrice, "매도 조건 가격은 0보다 커야 합니다.");
            this.sellConditionPrice = sellConditionPrice;
        }

        if (stopLossRate != null) {
            validatePositive(stopLossRate, "손절률은 0보다 커야합니다.");
            this.stopLossRate = stopLossRate;
        }

        if (targetReturnRate != null) {
            validatePositive(targetReturnRate, "목표 수익률은 0보다 커야합니다.");
            this.targetReturnRate = targetReturnRate;
        }

        if (allocatedAmount != null) {
            validatePositive(allocatedAmount, "전략 배정 금액은 0보다 커야 합니다.");
            this.allocatedAmount = allocatedAmount;
        }

        if (perCondition != null) {
            validatePositive(perCondition, "PER 조건은 0보다 커야 합니다.");
            this.perCondition = perCondition;
        }

        if (pbrCondition != null) {
            validatePositive(pbrCondition, "PBR 조건은 0보다 커야 합니다.");
            this.pbrCondition = pbrCondition;
        }

        if (roeCondition != null) {
            validatePositive(roeCondition, "ROE 조건은 0보다 커야 합니다.");
            this.roeCondition = roeCondition;
        }
    }

    private static void validatePositive(Long value, String message) {
        if (value <= 0) {
            throw new BusinessException(StrategyErrorCode.INVALID_STRATEGY, message);
        }
    }

    private static void validatePositive(BigDecimal value, String message) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(StrategyErrorCode.INVALID_STRATEGY, message);
        }
    }

    public void delete(UUID deletedBy) {
        this.status = StrategyStatus.DELETED;
        softDelete(deletedBy);
    }
}
