package com.sajo.trading_service.trading.event;

import com.sajo.trading_service.trading.service.command.KisOrderCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderRequestedEventListenerTest {

    @Mock
    private KisOrderCommandService kisOrderCommandService;

    @InjectMocks
    private OrderRequestedEventListener listener;

    @Test
    @DisplayName("Order 생성 이벤트를 수신하면 KIS 주문을 실행한다")
    void handleOrderRequestedEvent() {
        // given
        UUID orderId = UUID.randomUUID();

        OrderRequestedEvent event =
                new OrderRequestedEvent(orderId);

        // when
        listener.handle(event);

        // then
        verify(kisOrderCommandService)
                .executeOrder(orderId);
    }

    @Test
    @DisplayName("KIS 주문 실행 중 예외가 발생해도 Listener에서 예외를 처리한다")
    void handleOrderRequestedEventWhenKisOrderFails() {
        // given
        UUID orderId = UUID.randomUUID();

        OrderRequestedEvent event =
                new OrderRequestedEvent(orderId);

        doThrow(new RuntimeException("unexpected"))
                .when(kisOrderCommandService)
                .executeOrder(orderId);

        // when & then
        assertThatCode(() ->
                listener.handle(event)
        ).doesNotThrowAnyException();

        verify(kisOrderCommandService)
                .executeOrder(orderId);
    }
}