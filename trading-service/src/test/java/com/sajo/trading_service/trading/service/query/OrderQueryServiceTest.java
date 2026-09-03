package com.sajo.trading_service.trading.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.controller.dto.response.OrderDetailResponse;
import com.sajo.trading_service.trading.controller.dto.response.OrderListResponse;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.query.OrderQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    @Mock
    private OrderQueryRepository orderQueryRepository;

    @InjectMocks
    private OrderQueryService orderQueryService;

    private UUID userId;
    private UUID orderId;
    private UUID autoTradingId;
    private UUID strategyId;
    private UUID signalId;
    private Order order;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        autoTradingId = UUID.randomUUID();
        strategyId = UUID.randomUUID();
        signalId = UUID.randomUUID();

        order = mock(Order.class);
    }

    @Test
    @DisplayName("사용자의 주문 목록을 조회한다")
    void findOrdersByUserId_success() {
        // given
        Pageable pageable = PageRequest.of(0, 10);

        when(order.getId()).thenReturn(orderId);
        when(order.getAutoTradingId()).thenReturn(autoTradingId);
        when(order.getStrategyId()).thenReturn(strategyId);
        when(order.getStockCode()).thenReturn("005930");
        when(order.getOrderType()).thenReturn(OrderType.BUY);
        when(order.getSignalPrice()).thenReturn(69900L);
        when(order.getOrderQuantity()).thenReturn(7);
        when(order.getEstimatedOrderAmount()).thenReturn(489300L);
        when(order.getStatus()).thenReturn(OrderStatus.REQUESTED);
        when(order.getCreatedAt()).thenReturn(Instant.now());

        Page<Order> orders =
                new PageImpl<>(
                        List.of(order),
                        pageable,
                        1
                );

        when(orderQueryRepository.findByUserId(userId, pageable))
                .thenReturn(orders);

        // when
        Page<OrderListResponse> result =
                orderQueryService.findOrdersByUserId(
                        userId,
                        pageable
                );

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);

        OrderListResponse response = result.getContent().get(0);

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.autoTradingId()).isEqualTo(autoTradingId);
        assertThat(response.strategyId()).isEqualTo(strategyId);
        assertThat(response.stockCode()).isEqualTo("005930");
        assertThat(response.orderType()).isEqualTo(OrderType.BUY);
        assertThat(response.status()).isEqualTo(OrderStatus.REQUESTED);
    }

    @Test
    @DisplayName("주문 목록이 없으면 빈 페이지를 반환한다")
    void findOrdersByUserId_empty() {
        // given
        Pageable pageable = PageRequest.of(0, 10);

        when(orderQueryRepository.findByUserId(userId, pageable))
                .thenReturn(Page.empty(pageable));

        // when
        Page<OrderListResponse> result =
                orderQueryService.findOrdersByUserId(
                        userId,
                        pageable
                );

        // then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("사용자의 주문 상세 정보를 조회한다")
    void findOrderByIdAndUserId_success() {
        // given
        when(order.getId()).thenReturn(orderId);
        when(order.getAutoTradingId()).thenReturn(autoTradingId);
        when(order.getStrategyId()).thenReturn(strategyId);
        when(order.getSignalId()).thenReturn(signalId);
        when(order.getStockCode()).thenReturn("005930");
        when(order.getOrderType()).thenReturn(OrderType.BUY);
        when(order.getSignalPrice()).thenReturn(69900L);
        when(order.getOrderQuantity()).thenReturn(7);
        when(order.getEstimatedOrderAmount()).thenReturn(489300L);
        when(order.getStatus()).thenReturn(OrderStatus.ACCEPTED);

        when(orderQueryRepository.findByIdAndUserId(orderId, userId))
                .thenReturn(Optional.of(order));

        // when
        OrderDetailResponse result =
                orderQueryService.findOrderByIdAndUserId(
                        orderId,
                        userId
                );

        // then
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.autoTradingId()).isEqualTo(autoTradingId);
        assertThat(result.strategyId()).isEqualTo(strategyId);
        assertThat(result.signalId()).isEqualTo(signalId);
        assertThat(result.stockCode()).isEqualTo("005930");
        assertThat(result.orderType()).isEqualTo(OrderType.BUY);
        assertThat(result.status()).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    @DisplayName("주문을 찾을 수 없으면 예외가 발생한다")
    void findOrderByIdAndUserId_notFound() {
        // given
        when(orderQueryRepository.findByIdAndUserId(orderId, userId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                orderQueryService.findOrderByIdAndUserId(
                        orderId,
                        userId
                ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(TradingErrorCode.ORDER_NOT_FOUND);
                });
    }
}