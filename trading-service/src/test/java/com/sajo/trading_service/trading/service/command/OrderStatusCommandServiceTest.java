package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.command.OrderCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderStatusCommandServiceTest {

    @Mock
    private OrderCommandRepository orderCommandRepository;

    @InjectMocks
    private OrderStatusCommandService orderStatusCommandService;

    private UUID orderId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
    }

    @Test
    void REQUESTED_주문의_실행권을_선점한다() {
        Order order = mock(Order.class);

        when(orderCommandRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        orderStatusCommandService.startProcessing(orderId);

        verify(orderCommandRepository)
                .findByIdForUpdate(orderId);

        verify(order)
                .startProcessing();
    }

    @Test
    void 선점하려는_Order가_없으면_예외가_발생한다() {
        when(orderCommandRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> orderStatusCommandService.startProcessing(orderId)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    TradingErrorCode.ORDER_NOT_FOUND
                            );
                });

        verifyNoMoreInteractions(orderCommandRepository);
    }

    @Test
    @DisplayName("PROCESSING 주문을 REQUESTED 상태로 재시도한다")
    void retryOrder() {
        // given
        Order order = Order.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "005930",
                OrderType.BUY,
                70_000L,
                4
        );

        order.startProcessing();

        when(orderCommandRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        // when
        orderStatusCommandService.retry(orderId);

        // then
        verify(orderCommandRepository)
                .findByIdForUpdate(orderId);

        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.REQUESTED);

        assertThat(order.getAccountRetryCount())
                .isEqualTo(1);
    }
}