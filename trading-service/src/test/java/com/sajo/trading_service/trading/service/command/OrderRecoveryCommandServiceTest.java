package com.sajo.trading_service.trading.service.command;

import com.sajo.trading_service.trading.repository.command.OrderCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderRecoveryCommandServiceTest {

    @Mock
    private OrderCommandRepository orderCommandRepository;

    @Mock
    private KisOrderCommandService kisOrderCommandService;

    @Mock
    private OrderStatusCommandService orderStatusCommandService;

    @InjectMocks
    private OrderRecoveryCommandService orderRecoveryCommandService;

    private UUID orderId1;
    private UUID orderId2;

    @BeforeEach
    void setUp() {
        orderId1 = UUID.randomUUID();
        orderId2 = UUID.randomUUID();
    }

    @Test
    @DisplayName("오래된 REQUESTED 주문은 KIS 주문 실행을 다시 시도한다")
    void recoverRequestedOrders() {
        // given
        when(orderCommandRepository.findStaleRequestedOrderIds(any(Instant.class)))
                .thenReturn(List.of(orderId1, orderId2));

        // when
        orderRecoveryCommandService.recoverRequestedOrders();

        // then
        verify(kisOrderCommandService)
                .executeOrder(orderId1);

        verify(kisOrderCommandService)
                .executeOrder(orderId2);
    }

    @Test
    @DisplayName("오래된 PROCESSING 주문은 TIMEOUT 처리한다")
    void recoverProcessingOrders() {
        // given
        when(orderCommandRepository.findStaleProcessingOrderIds(any(Instant.class)))
                .thenReturn(List.of(orderId1, orderId2));

        // when
        orderRecoveryCommandService.recoverProcessingOrders();

        // then
        verify(orderStatusCommandService)
                .timeout(
                        orderId1,
                        "ORDER_PROCESSING_TIMEOUT",
                        "주문 처리 결과를 확인할 수 없습니다."
                );

        verify(orderStatusCommandService)
                .timeout(
                        orderId2,
                        "ORDER_PROCESSING_TIMEOUT",
                        "주문 처리 결과를 확인할 수 없습니다."
                );
    }

    @Test
    @DisplayName("REQUESTED 주문 복구 중 하나가 실패해도 다음 주문을 계속 처리한다")
    void recoverRequestedOrdersContinuesAfterFailure() {
        // given
        when(orderCommandRepository.findStaleRequestedOrderIds(any(Instant.class)))
                .thenReturn(List.of(orderId1, orderId2));

        doThrow(new RuntimeException("unexpected"))
                .when(kisOrderCommandService)
                .executeOrder(orderId1);

        // when
        orderRecoveryCommandService.recoverRequestedOrders();

        // then
        verify(kisOrderCommandService)
                .executeOrder(orderId1);

        verify(kisOrderCommandService)
                .executeOrder(orderId2);
    }

    @Test
    @DisplayName("PROCESSING 주문 복구 중 하나가 실패해도 다음 주문을 계속 처리한다")
    void recoverProcessingOrdersContinuesAfterFailure() {
        // given
        when(orderCommandRepository.findStaleProcessingOrderIds(any(Instant.class)))
                .thenReturn(List.of(orderId1, orderId2));

        doThrow(new RuntimeException("unexpected"))
                .when(orderStatusCommandService)
                .timeout(
                        eq(orderId1),
                        anyString(),
                        anyString()
                );

        // when
        orderRecoveryCommandService.recoverProcessingOrders();

        // then
        verify(orderStatusCommandService)
                .timeout(
                        eq(orderId2),
                        anyString(),
                        anyString()
                );
    }
}