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

    @Test
    @DisplayName("REQUESTED 상태의 주문을 PROCESSING으로 변경할 수 있다")
    void startProcessingFromRequested() {
        // given
        Order order = createOrder();

        // when
        order.startProcessing();

        // then
        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    @DisplayName("REQUESTED 상태가 아니면 PROCESSING으로 변경할 수 없다")
    void startProcessingNotAllowed() {
        // given
        Order order = createOrder();
        order.startProcessing();

        // when & then
        assertThatThrownBy(order::startProcessing)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    TradingErrorCode.ORDER_EXECUTION_NOT_ALLOWED
                            );
                });
    }

    @Test
    @DisplayName("PROCESSING 상태의 주문을 ACCEPTED로 변경할 수 있다")
    void acceptFromProcessing() {
        // given
        Order order = createOrder();
        order.startProcessing();

        // when
        order.accept("123456");

        // then
        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.ACCEPTED);
        assertThat(order.getBrokerOrderNo())
                .isEqualTo("123456");
        assertThat(order.getFailureCode())
                .isNull();
        assertThat(order.getFailureMessage())
                .isNull();
    }

    @Test
    @DisplayName("TIMEOUT 상태의 주문을 ACCEPTED로 변경할 수 있다")
    void acceptFromTimeout() {
        // given
        Order order = createOrder();
        order.startProcessing();
        order.timeout(
                "TIMEOUT",
                "KIS 주문 응답 타임아웃"
        );

        // when
        order.accept("123456");

        // then
        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.ACCEPTED);
        assertThat(order.getBrokerOrderNo())
                .isEqualTo("123456");
        assertThat(order.getFailureCode())
                .isNull();
        assertThat(order.getFailureMessage())
                .isNull();
    }

    @Test
    @DisplayName("PROCESSING 상태의 주문을 FAILED로 변경할 수 있다")
    void failFromProcessing() {
        // given
        Order order = createOrder();
        order.startProcessing();

        // when
        order.fail(
                "KIS_ERROR",
                "주문이 거절되었습니다."
        );

        // then
        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.FAILED);
        assertThat(order.getFailureCode())
                .isEqualTo("KIS_ERROR");
        assertThat(order.getFailureMessage())
                .isEqualTo("주문이 거절되었습니다.");
    }

    @Test
    @DisplayName("TIMEOUT 상태의 주문을 FAILED로 변경할 수 있다")
    void failFromTimeout() {
        // given
        Order order = createOrder();
        order.startProcessing();
        order.timeout(
                "TIMEOUT",
                "KIS 주문 응답 타임아웃"
        );

        // when
        order.fail(
                "KIS_REJECTED",
                "주문 실패가 확인되었습니다."
        );

        // then
        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.FAILED);
        assertThat(order.getFailureCode())
                .isEqualTo("KIS_REJECTED");
        assertThat(order.getFailureMessage())
                .isEqualTo("주문 실패가 확인되었습니다.");
    }

    @Test
    @DisplayName("PROCESSING 상태의 주문을 TIMEOUT으로 변경할 수 있다")
    void timeoutFromProcessing() {
        // given
        Order order = createOrder();
        order.startProcessing();

        // when
        order.timeout(
                "TIMEOUT",
                "KIS 주문 응답 타임아웃"
        );

        // then
        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.TIMEOUT);
        assertThat(order.getFailureCode())
                .isEqualTo("TIMEOUT");
        assertThat(order.getFailureMessage())
                .isEqualTo("KIS 주문 응답 타임아웃");
    }

    @Test
    @DisplayName("허용되지 않은 상태에서는 ACCEPTED로 변경할 수 없다")
    void acceptNotAllowed() {
        // given
        Order order = createOrder();
        order.startProcessing();
        order.fail(
                "KIS_ERROR",
                "주문 실패"
        );

        // when & then
        assertThatThrownBy(() ->
                order.accept("123456")
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                TradingErrorCode.ORDER_STATUS_CHANGE_NOT_ALLOWED
                        )
                );
    }

    @Test
    @DisplayName("brokerOrderNo가 비어있으면 ACCEPTED로 변경할 수 없다")
    void acceptWithoutBrokerOrderNo() {
        // given
        Order order = createOrder();
        order.startProcessing();

        // when & then
        assertThatThrownBy(() ->
                order.accept("")
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                TradingErrorCode.INVALID_ORDER
                        )
                );
    }

    private Order createOrder() {
        return Order.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "005930",
                OrderType.BUY,
                70_000L,
                4
        );
    }
}