package com.sajo.market_service.strategy.domain;

import com.sajo.common.entity.BaseUpdatableEntity;
import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;

@Getter
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

    @Column(name = "target_return_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal targetReturnRate;

    @Column(name = "allocated_amount", nullable = false)
    private Long allocatedAmount;

    @Column(name = "per_condition", nullable = false, precision = 10, scale = 4)
    private BigDecimal perCondition;

    @Column(name = "pbr_condition", nullable = false, precision = 10, scale = 4)
    private BigDecimal pbrCondition;

    @Column(name = "roe_condition", nullable = false, precision = 10, scale = 4)
    private BigDecimal roeCondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StrategyStatus status;

    @Column(name = "activated_at")
    private Timestamp activatedAt;

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
    }
}
