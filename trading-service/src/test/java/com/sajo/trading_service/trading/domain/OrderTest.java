package com.sajo.trading_service.trading.domain;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    @DisplayName("Order 생성 시 상태는 REQUESTED이고 예상 주문 금액을 계산한다")
    void createOrder_success() {
        // given
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID signalId = UUID.randomUUID();

        long signalPrice = 69900L;
        int orderQuantity = 7;

        // when
        Order order = Order.create(
                userId,
                autoTradingId,
                strategyId,
                signalId,
                "005930",
                OrderType.BUY,
                signalPrice,
                orderQuantity
        );

        // then
        assertThat(order.getUserId()).isEqualTo(userId);
        assertThat(order.getAutoTradingId()).isEqualTo(autoTradingId);
        assertThat(order.getStrategyId()).isEqualTo(strategyId);
        assertThat(order.getSignalId()).isEqualTo(signalId);
        assertThat(order.getStockCode()).isEqualTo("005930");
        assertThat(order.getOrderType()).isEqualTo(OrderType.BUY);
        assertThat(order.getSignalPrice()).isEqualTo(signalPrice);
        assertThat(order.getOrderQuantity()).isEqualTo(orderQuantity);
        assertThat(order.getEstimatedOrderAmount())
                .isEqualTo(signalPrice * orderQuantity);
        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.REQUESTED);
    }

    @Test
    @DisplayName("Signal 가격이 0 이하이면 Order 생성에 실패한다")
    void createOrder_invalidSignalPrice() {
        // given
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID signalId = UUID.randomUUID();

        // when & then
        assertThatThrownBy(() ->
                Order.create(
                        userId,
                        autoTradingId,
                        strategyId,
                        signalId,
                        "005930",
                        OrderType.BUY,
                        0L,
                        7
                ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(TradingErrorCode.INVALID_ORDER);
                });
    }

    @Test
    @DisplayName("주문 수량이 0 이하이면 Order 생성에 실패한다")
    void createOrder_invalidOrderQuantity() {
        // given
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID signalId = UUID.randomUUID();

        // when & then
        assertThatThrownBy(() ->
                Order.create(
                        userId,
                        autoTradingId,
                        strategyId,
                        signalId,
                        "005930",
                        OrderType.BUY,
                        69900L,
                        0
                ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(TradingErrorCode.INVALID_ORDER);
                });
    }

    @Test
    @DisplayName("종목 코드가 비어 있으면 Order 생성에 실패한다")
    void createOrder_blankStockCode() {
        // given
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID signalId = UUID.randomUUID();

        // when & then
        assertThatThrownBy(() ->
                Order.create(
                        userId,
                        autoTradingId,
                        strategyId,
                        signalId,
                        " ",
                        OrderType.BUY,
                        69900L,
                        7
                ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(TradingErrorCode.INVALID_ORDER);
                });
    }

    @Test
    @DisplayName("필수 식별자가 없으면 Order 생성에 실패한다")
    void createOrder_nullRequiredId() {
        // when & then
        assertThatThrownBy(() ->
                Order.create(
                        null,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "005930",
                        OrderType.BUY,
                        69900L,
                        7
                ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(TradingErrorCode.INVALID_ORDER);
                });
    }
}