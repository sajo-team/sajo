package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.controller.dto.request.OrderManualResolutionRequest;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.OrderManualResolution;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.command.OrderCommandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderAdminCommandServiceTest {

    @Mock
    private OrderCommandRepository orderCommandRepository;

    @Mock
    private OrderExecutionCommandService orderExecutionCommandService;

    @InjectMocks
    private OrderAdminCommandService orderAdminCommandService;

    @Test
    @DisplayName("관리자는 재조정 소진 TIMEOUT 주문을 FAILED로 수동 확정할 수 있다")
    void resolveTimeoutOrder_failed() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = mock(Order.class);

        when(orderCommandRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        OrderManualResolutionRequest request =
                new OrderManualResolutionRequest(
                        OrderManualResolution.FAILED,
                        null,
                        null,
                        null,
                        null,
                        "KIS 확인 결과 주문 접수 내역 없음"
                );

        // when
        orderAdminCommandService.resolveTimeoutOrder(
                orderId,
                request
        );

        // then
        verify(order)
                .validateManualResolutionAllowed();

        verify(order)
                .fail(
                        "MANUAL_RESOLUTION_FAILED",
                        "KIS 확인 결과 주문 접수 내역 없음"
                );

        verifyNoInteractions(orderExecutionCommandService);
    }

    @Test
    @DisplayName("관리자는 재조정 소진 TIMEOUT 주문을 ACCEPTED로 복구할 수 있다")
    void resolveTimeoutOrder_accepted() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = mock(Order.class);

        when(orderCommandRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        OrderManualResolutionRequest request =
                new OrderManualResolutionRequest(
                        OrderManualResolution.ACCEPTED,
                        "0001234567",
                        null,
                        null,
                        null,
                        "KIS에서 정상 접수 확인"
                );

        // when
        orderAdminCommandService.resolveTimeoutOrder(
                orderId,
                request
        );

        // then
        verify(order)
                .validateManualResolutionAllowed();

        verify(order)
                .accept("0001234567");

        verifyNoInteractions(orderExecutionCommandService);
    }

    @Test
    @DisplayName("체결 없이 취소된 TIMEOUT 주문은 Execution 생성 없이 취소 처리한다")
    void resolveTimeoutOrder_canceledWithoutFill() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = mock(Order.class);

        when(orderCommandRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        OrderManualResolutionRequest request =
                new OrderManualResolutionRequest(
                        OrderManualResolution.CANCELED,
                        "0001234567",
                        0,
                        null,
                        null,
                        "KIS 확인 결과 미체결 취소"
                );

        // when
        orderAdminCommandService.resolveTimeoutOrder(
                orderId,
                request
        );

        // then
        verify(order)
                .validateManualResolutionAllowed();

        verify(orderExecutionCommandService)
                .applyReconciledCancellation(
                        order,
                        "0001234567",
                        0,
                        BigDecimal.ZERO,
                        0L
                );

        verify(order, never())
                .reconcileCanceled(
                        anyString(),
                        anyInt()
                );
    }

    @Test
    @DisplayName("부분 체결 후 취소된 TIMEOUT 주문은 Execution 정합성 처리 서비스를 호출한다")
    void resolveTimeoutOrder_canceledWithPartialFill() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = mock(Order.class);

        when(orderCommandRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        OrderManualResolutionRequest request =
                new OrderManualResolutionRequest(
                        OrderManualResolution.CANCELED,
                        "0001234567",
                        3,
                        new BigDecimal("70000"),
                        210_000L,
                        "3주 체결 후 잔량 취소 확인"
                );

        // when
        orderAdminCommandService.resolveTimeoutOrder(
                orderId,
                request
        );

        // then
        verify(order)
                .validateManualResolutionAllowed();

        verify(orderExecutionCommandService)
                .applyReconciledCancellation(
                        order,
                        "0001234567",
                        3,
                        new BigDecimal("70000"),
                        210_000L
                );
    }

    @Test
    @DisplayName("취소 확정 시 체결 수량이 없으면 실패한다")
    void resolveTimeoutOrder_canceledWithoutFilledQuantity() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = mock(Order.class);

        when(orderCommandRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        OrderManualResolutionRequest request =
                new OrderManualResolutionRequest(
                        OrderManualResolution.CANCELED,
                        "0001234567",
                        null,
                        null,
                        null,
                        "취소 확인"
                );

        // when & then
        assertThrows(
                BusinessException.class,
                () -> orderAdminCommandService.resolveTimeoutOrder(
                        orderId,
                        request
                )
        );

        verify(order)
                .validateManualResolutionAllowed();

        verifyNoInteractions(orderExecutionCommandService);
    }

    @Test
    @DisplayName("부분 체결 취소인데 평균 체결가가 없으면 실패한다")
    void resolveTimeoutOrder_partialCanceledWithoutAveragePrice() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = mock(Order.class);

        when(orderCommandRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        OrderManualResolutionRequest request =
                new OrderManualResolutionRequest(
                        OrderManualResolution.CANCELED,
                        "0001234567",
                        3,
                        null,
                        210_000L,
                        "부분 체결 후 취소"
                );

        // when & then
        assertThrows(
                BusinessException.class,
                () -> orderAdminCommandService.resolveTimeoutOrder(
                        orderId,
                        request
                )
        );

        verifyNoInteractions(orderExecutionCommandService);
    }

    @Test
    @DisplayName("수동 확정 대상이 아닌 주문은 처리하지 않는다")
    void resolveTimeoutOrder_notAllowed() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = mock(Order.class);

        when(orderCommandRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        doThrow(
                new BusinessException(
                        TradingErrorCode.ORDER_MANUAL_RESOLUTION_NOT_ALLOWED
                )
        ).when(order)
                .validateManualResolutionAllowed();

        OrderManualResolutionRequest request =
                new OrderManualResolutionRequest(
                        OrderManualResolution.FAILED,
                        null,
                        null,
                        null,
                        null,
                        "관리자 확인"
                );

        // when & then
        assertThrows(
                BusinessException.class,
                () -> orderAdminCommandService.resolveTimeoutOrder(
                        orderId,
                        request
                )
        );

        verify(order, never())
                .fail(any(), any());

        verify(order, never())
                .accept(any());

        verifyNoInteractions(orderExecutionCommandService);
    }

    @Test
    @DisplayName("존재하지 않는 주문을 수동 확정하면 실패한다")
    void resolveTimeoutOrder_notFound() {
        // given
        UUID orderId = UUID.randomUUID();

        when(orderCommandRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.empty());

        OrderManualResolutionRequest request =
                new OrderManualResolutionRequest(
                        OrderManualResolution.FAILED,
                        null,
                        null,
                        null,
                        null,
                        "관리자 확인"
                );

        // when & then
        assertThrows(
                BusinessException.class,
                () -> orderAdminCommandService.resolveTimeoutOrder(
                        orderId,
                        request
                )
        );

        verifyNoInteractions(orderExecutionCommandService);
    }
}