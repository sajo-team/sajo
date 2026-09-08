package com.sajo.trading_service.trading.domain;

import com.sajo.common.entity.BaseUpdatableEntity;
import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "p_executions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Execution extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "executed_quantity", nullable = false)
    private Integer executedQuantity;

    @Column(name = "average_execution_price", nullable = false)
    private Long averageExecutionPrice;

    @Column(name = "total_execution_amount", nullable = false)
    private Long totalExecutionAmount;

    @Column(name = "remaining_quantity", nullable = false)
    private Integer remainingQuantity;

    private Execution(
            UUID orderId,
            Integer executedQuantity,
            Long averageExecutionPrice,
            Long totalExecutionAmount,
            Integer remainingQuantity
    ) {
        this.orderId = orderId;
        this.executedQuantity = executedQuantity;
        this.averageExecutionPrice = averageExecutionPrice;
        this.totalExecutionAmount = totalExecutionAmount;
        this.remainingQuantity = remainingQuantity;
    }

    public static Execution create(
            UUID orderId,
            Integer executedQuantity,
            Long averageExecutionPrice,
            Long totalExecutionAmount,
            Integer remainingQuantity
    ) {
        if (orderId == null
                || executedQuantity == null
                || executedQuantity < 0
                || averageExecutionPrice == null
                || averageExecutionPrice < 0
                || totalExecutionAmount == null
                || totalExecutionAmount < 0
                || remainingQuantity == null
                || remainingQuantity < 0) {
            throw new BusinessException(
                    TradingErrorCode.INVALID_ORDER
            );
        }

        return new Execution(
                orderId,
                executedQuantity,
                averageExecutionPrice,
                totalExecutionAmount,
                remainingQuantity
        );
    }

    public void update(
            Integer executedQuantity,
            Long averageExecutionPrice,
            Long totalExecutionAmount,
            Integer remainingQuantity
    ) {
        if (executedQuantity == null
                || executedQuantity < 0
                || averageExecutionPrice == null
                || averageExecutionPrice < 0
                || totalExecutionAmount == null
                || totalExecutionAmount < 0
                || remainingQuantity == null
                || remainingQuantity < 0) {
            throw new BusinessException(
                    TradingErrorCode.INVALID_ORDER
            );
        }

        if (executedQuantity < this.executedQuantity) {
            throw new BusinessException(
                    TradingErrorCode.INVALID_ORDER
            );
        }

        this.executedQuantity = executedQuantity;
        this.averageExecutionPrice = averageExecutionPrice;
        this.totalExecutionAmount = totalExecutionAmount;
        this.remainingQuantity = remainingQuantity;
    }
}