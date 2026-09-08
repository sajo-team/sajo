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

    @Column(name = "filled_quantity", nullable = false)
    private Integer filledQuantity;

    @Column(name = "remaining_quantity", nullable = false)
    private Integer remainingQuantity;

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

    @Column(name = "account_retry_count", nullable = false)
    private Integer accountRetryCount;

    @Column(name = "reconciliation_retry_count", nullable = false)
    private Integer reconciliationRetryCount;

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
        this.filledQuantity = 0;
        this.remainingQuantity = orderQuantity;
        this.estimatedOrderAmount =
                signalPrice * orderQuantity.longValue();
        this.status = OrderStatus.REQUESTED;
        this.accountRetryCount = 0;
        this.reconciliationRetryCount = 0;
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
        if(this.status != OrderStatus.PROCESSING
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
        if(this.status != OrderStatus.PROCESSING
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
        if(this.status != OrderStatus.PROCESSING){
            throw new BusinessException(
                    TradingErrorCode.ORDER_STATUS_CHANGE_NOT_ALLOWED
            );
        }
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.status = OrderStatus.TIMEOUT;
    }

    public void startProcessing(){
        if(this.status != OrderStatus.REQUESTED){
            throw new BusinessException(
                    TradingErrorCode.ORDER_EXECUTION_NOT_ALLOWED
            );
        }
        this.status = OrderStatus.PROCESSING;
    }

    public void retry(
            int maxRetryCount,
            String failureCode,
            String failureMessage
    ) {
        if (this.status != OrderStatus.PROCESSING) {
            throw new BusinessException(
                    TradingErrorCode.ORDER_STATUS_CHANGE_NOT_ALLOWED
            );
        }

        this.accountRetryCount++;

        if (this.accountRetryCount >= maxRetryCount) {
            this.status = OrderStatus.FAILED;
            this.failureCode = failureCode;
            this.failureMessage = failureMessage;
            return;
        }

        this.status = OrderStatus.REQUESTED;
        this.failureCode = null;
        this.failureMessage = null;
    }

    public void recordReconciliationFailure(
            int maxRetryCount,
            String failureCode,
            String failureMessage
    ) {
        if (this.status != OrderStatus.PROCESSING
                && this.status != OrderStatus.TIMEOUT) {
            throw new BusinessException(
                    TradingErrorCode.ORDER_STATUS_CHANGE_NOT_ALLOWED
            );
        }

        this.reconciliationRetryCount++;

        if (this.reconciliationRetryCount >= maxRetryCount) {
            this.status = OrderStatus.FAILED;
            this.failureCode = failureCode;
            this.failureMessage = failureMessage;
        }
    }

    public void timeoutWithBrokerOrderNo(
            String brokerOrderNo,
            String failureCode,
            String failureMessage
    ) {
        if (this.status != OrderStatus.PROCESSING) {
            throw new BusinessException(
                    TradingErrorCode.ORDER_STATUS_CHANGE_NOT_ALLOWED
            );
        }

        if (brokerOrderNo == null || brokerOrderNo.isBlank()) {
            throw new BusinessException(
                    TradingErrorCode.INVALID_ORDER
            );
        }

        this.status = OrderStatus.TIMEOUT;
        this.brokerOrderNo = brokerOrderNo;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    public int applyFill(
            int totalFilledQuantity,
            int remainingQuantity
    ) {
        if (this.status != OrderStatus.ACCEPTED
                && this.status != OrderStatus.PARTIALLY_FILLED) {
            throw new BusinessException(
                    TradingErrorCode.ORDER_STATUS_CHANGE_NOT_ALLOWED
            );
        }

        if (totalFilledQuantity < 0
                || remainingQuantity < 0
                || totalFilledQuantity > this.orderQuantity) {
            throw new BusinessException(
                    TradingErrorCode.INVALID_ORDER
            );
        }

        /*
         * 이미 확인한 누적 체결 수량보다 작은 값으로 되돌아가는 것을 방지한다.
         */
        if (totalFilledQuantity < this.filledQuantity) {
            throw new BusinessException(
                    TradingErrorCode.INVALID_ORDER
            );
        }

        /*
         * 취소되지 않은 일반 주문 기준으로
         * 체결 수량 + 미체결 수량은 주문 수량과 일치해야 한다.
         *
         * 취소/부분체결 후 취소는 별도 정책으로 처리한다.
         */
        if (totalFilledQuantity + remainingQuantity
                != this.orderQuantity) {
            throw new BusinessException(
                    TradingErrorCode.INVALID_ORDER
            );
        }

        int newlyFilledQuantity =
                totalFilledQuantity - this.filledQuantity;

        /*
         * 동일한 누적 체결 결과를 다시 조회한 경우
         * 상태나 수량을 중복 변경하지 않는다.
         */
        if (newlyFilledQuantity == 0) {
            return 0;
        }

        this.filledQuantity = totalFilledQuantity;
        this.remainingQuantity = remainingQuantity;

        if (totalFilledQuantity == this.orderQuantity) {
            this.status = OrderStatus.FILLED;
        } else {
            this.status = OrderStatus.PARTIALLY_FILLED;
        }

        return newlyFilledQuantity;
    }

    public int cancel(
            int totalFilledQuantity,
            int remainingQuantity
    ) {
        if (this.status != OrderStatus.ACCEPTED
                && this.status != OrderStatus.PARTIALLY_FILLED) {
            throw new BusinessException(
                    TradingErrorCode.ORDER_STATUS_CHANGE_NOT_ALLOWED
            );
        }

        if (totalFilledQuantity < 0
                || remainingQuantity < 0
                || totalFilledQuantity > this.orderQuantity) {
            throw new BusinessException(
                    TradingErrorCode.INVALID_ORDER
            );
        }

        if (totalFilledQuantity < this.filledQuantity) {
            throw new BusinessException(
                    TradingErrorCode.INVALID_ORDER
            );
        }

        int newlyFilledQuantity =
                totalFilledQuantity - this.filledQuantity;

        this.filledQuantity = totalFilledQuantity;
        this.remainingQuantity = remainingQuantity;
        this.status = OrderStatus.CANCELED;

        return newlyFilledQuantity;
    }
}
