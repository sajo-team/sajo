package com.sajo.trading_service.trading.domain;

import com.sajo.common.entity.BaseUpdatableEntity;
import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "p_orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseUpdatableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "auto_trading_id", nullable = false)
    private UUID autoTradingId;

    @Column(name = "strategy_id", nullable = false)
    private UUID strategyId;

    @Column(name = "signal_id", nullable = false, unique = true)
    private UUID signalId;

    @Column(name = "stock_code", nullable = false)
    private String stockCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType orderType;

    @Column(name = "signal_price", nullable = false)
    private Long signalPrice;

    @Column(name = "order_quantity", nullable = false)
    private Integer orderQuantity;

    @Column(name = "estimated_order_amount", nullable = false)
    private Long estimatedOrderAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "broker_order_no")
    private String brokerOrderNo;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_message")
    private String failureMessage;

    private Order(
            UUID userId,
            UUID autoTradingId,
            UUID strategyId,
            UUID signalId,
            String stockCode,
            OrderType orderType,
            Long signalPrice,
            Integer orderQuantity
    ){
        this.userId = userId;
        this.autoTradingId = autoTradingId;
        this.strategyId = strategyId;
        this.signalId = signalId;
        this.stockCode = stockCode;
        this.orderType = orderType;
        this.signalPrice = signalPrice;
        this.orderQuantity = orderQuantity;
        this.estimatedOrderAmount =
                signalPrice * orderQuantity.longValue();
        this.status = OrderStatus.REQUESTED;
    }

    public static Order create(
            UUID userId,
            UUID autoTradingId,
            UUID strategyId,
            UUID signalId,
            String stockCode,
            OrderType orderType,
            Long signalPrice,
            Integer orderQuantity
    ) {
        if (userId == null
                || autoTradingId == null
                || strategyId == null
                || signalId == null
                || stockCode == null
                || stockCode.isBlank()
                || orderType == null
                || signalPrice == null
                || signalPrice <= 0
                || orderQuantity == null
                || orderQuantity <= 0) {
            throw new BusinessException(
                    TradingErrorCode.INVALID_ORDER
            );
        }

        return new Order(
                userId,
                autoTradingId,
                strategyId,
                signalId,
                stockCode,
                orderType,
                signalPrice,
                orderQuantity
        );
    }

    public void accept(String brokerOrderNo){
        if(this.status != OrderStatus.REQUESTED
                && this.status != OrderStatus.TIMEOUT){
            throw new BusinessException(
                    TradingErrorCode.ORDER_STATUS_CHANGE_NOT_ALLOWED
            );
        }

        if(brokerOrderNo == null || brokerOrderNo.isBlank()){
            throw new BusinessException(
                    TradingErrorCode.INVALID_ORDER
            );
        }
        this.brokerOrderNo = brokerOrderNo;
        this.failureCode = null;
        this.failureMessage = null;
        this.status = OrderStatus.ACCEPTED;
    }

    public void fail(
            String failureCode,
            String failureMessage
    ){
        if(this.status != OrderStatus.REQUESTED
                && this.status != OrderStatus.TIMEOUT){
            throw new BusinessException(
                    TradingErrorCode.ORDER_STATUS_CHANGE_NOT_ALLOWED
            );
        }
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.status = OrderStatus.FAILED;
    }

    public void timeout(
            String failureCode,
            String failureMessage
    ){
        if(this.status != OrderStatus.REQUESTED){
            throw new BusinessException(
                    TradingErrorCode.ORDER_STATUS_CHANGE_NOT_ALLOWED
            );
        }
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.status = OrderStatus.TIMEOUT;
    }
}
