package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.domain.Execution;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.command.ExecutionCommandRepository;
import com.sajo.trading_service.trading.repository.command.OrderCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExecutionCommandServiceTest {

    @Mock
    private OrderCommandRepository orderCommandRepository;

    @Mock
    private ExecutionCommandRepository executionCommandRepository;

    private OrderExecutionCommandService orderExecutionCommandService;

    @BeforeEach
    void setUp() {
        orderExecutionCommandService =
                new OrderExecutionCommandService(
                        orderCommandRepository,
                        executionCommandRepository
                );
    }

    @Test
    @DisplayName("최초 부분 체결 시 주문 상태를 변경하고 Execution을 생성한다")
    void applyExecution_firstPartialFill_createExecution() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();

        when(orderCommandRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        when(executionCommandRepository.findByOrderId(orderId))
                .thenReturn(Optional.empty());

        // when
        orderExecutionCommandService.applyExecution(
                orderId,
                2,
                2,
                new BigDecimal("69800"),
                139_600L
        );

        // then
        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(order.getFilledQuantity()).isEqualTo(2);
        assertThat(order.getRemainingQuantity()).isEqualTo(2);

        ArgumentCaptor<Execution> executionCaptor =
                ArgumentCaptor.forClass(Execution.class);

        verify(executionCommandRepository)
                .save(executionCaptor.capture());

        Execution savedExecution = executionCaptor.getValue();

        assertThat(savedExecution.getOrderId()).isEqualTo(orderId);
        assertThat(savedExecution.getExecutedQuantity()).isEqualTo(2);
        assertThat(savedExecution.getRemainingQuantity()).isEqualTo(2);
        assertThat(savedExecution.getAverageExecutionPrice())
                .isEqualByComparingTo(new BigDecimal("69800"));
        assertThat(savedExecution.getTotalExecutionAmount())
                .isEqualTo(139_600L);
    }

    @Test
    @DisplayName("추가 체결 시 기존 Execution의 누적 체결 결과를 갱신한다")
    void applyExecution_additionalFill_updateExecution() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();

        order.applyFill(
                1,
                3
        );

        Execution execution =
                Execution.create(
                        orderId,
                        1,
                        new BigDecimal("69800"),
                        69_800L,
                        3
                );

        when(orderCommandRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        when(executionCommandRepository.findByOrderId(orderId))
                .thenReturn(Optional.of(execution));

        // when
        orderExecutionCommandService.applyExecution(
                orderId,
                3,
                1,
                new BigDecimal("69800"),
                209_700L
        );

        // then
        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(order.getFilledQuantity()).isEqualTo(3);
        assertThat(order.getRemainingQuantity()).isEqualTo(1);

        assertThat(execution.getExecutedQuantity()).isEqualTo(3);
        assertThat(execution.getRemainingQuantity()).isEqualTo(1);
        assertThat(execution.getAverageExecutionPrice())
                .isEqualByComparingTo(new BigDecimal("69800"));
        assertThat(execution.getTotalExecutionAmount())
                .isEqualTo(209_700L);

        verify(executionCommandRepository, never())
                .save(any(Execution.class));
    }

    @Test
    @DisplayName("전체 체결 시 주문 상태를 FILLED로 변경한다")
    void applyExecution_fullFill() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();

        when(orderCommandRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        when(executionCommandRepository.findByOrderId(orderId))
                .thenReturn(Optional.empty());

        // when
        orderExecutionCommandService.applyExecution(
                orderId,
                4,
                0,
                new BigDecimal("70000"),
                280_000L
        );

        // then
        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.FILLED);
        assertThat(order.getFilledQuantity()).isEqualTo(4);
        assertThat(order.getRemainingQuantity()).isZero();

        verify(executionCommandRepository)
                .save(any(Execution.class));
    }

    @Test
    @DisplayName("동일한 누적 체결 결과를 다시 조회하면 Execution을 중복 갱신하지 않는다")
    void applyExecution_sameQuantity_idempotent() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();

        order.applyFill(
                2,
                2
        );

        when(orderCommandRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        // when
        orderExecutionCommandService.applyExecution(
                orderId,
                2,
                2,
                new BigDecimal("69800"),
                139_600L
        );

        // then
        assertThat(order.getFilledQuantity()).isEqualTo(2);
        assertThat(order.getRemainingQuantity()).isEqualTo(2);
        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.PARTIALLY_FILLED);

        verify(executionCommandRepository, never())
                .findByOrderId(any(UUID.class));

        verify(executionCommandRepository, never())
                .save(any(Execution.class));
    }

    @Test
    @DisplayName("체결 없이 전체 취소되면 주문만 CANCELED로 변경하고 Execution은 생성하지 않는다")
    void applyCancellation_withoutFill() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();

        when(orderCommandRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        // when
        orderExecutionCommandService.applyCancellation(
                orderId,
                0,
                0,
                new BigDecimal("0"),
                0L
        );

        // then
        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getFilledQuantity()).isZero();
        assertThat(order.getRemainingQuantity()).isZero();

        verify(executionCommandRepository, never())
                .findByOrderId(any(UUID.class));

        verify(executionCommandRepository, never())
                .save(any(Execution.class));
    }

    @Test
    @DisplayName("부분 체결 후 추가 체결 없이 취소되어도 Execution의 잔여 수량을 0으로 갱신한다")
    void applyCancellation_afterPartialFill_updateRemainingQuantity() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();

        order.applyFill(
                2,
                2
        );

        Execution execution =
                Execution.create(
                        orderId,
                        2,
                        new BigDecimal("69800"),
                        139_600L,
                        2
                );

        when(orderCommandRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        when(executionCommandRepository.findByOrderId(orderId))
                .thenReturn(Optional.of(execution));

        // when
        orderExecutionCommandService.applyCancellation(
                orderId,
                2,
                0,
                new BigDecimal("69800"),
                139_600L
        );

        // then
        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getFilledQuantity()).isEqualTo(2);
        assertThat(order.getRemainingQuantity()).isZero();

        assertThat(execution.getExecutedQuantity()).isEqualTo(2);
        assertThat(execution.getRemainingQuantity()).isZero();

        verify(executionCommandRepository, never())
                .save(any(Execution.class));
    }

    @Test
    @DisplayName("부분 체결과 동시에 취소된 최초 조회라면 Execution을 생성한다")
    void applyCancellation_firstPartialFill_createExecution() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();

        when(orderCommandRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        when(executionCommandRepository.findByOrderId(orderId))
                .thenReturn(Optional.empty());

        // when
        orderExecutionCommandService.applyCancellation(
                orderId,
                2,
                0,
                new BigDecimal("69800"),
                139_600L
        );

        // then
        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getFilledQuantity()).isEqualTo(2);
        assertThat(order.getRemainingQuantity()).isZero();

        ArgumentCaptor<Execution> executionCaptor =
                ArgumentCaptor.forClass(Execution.class);

        verify(executionCommandRepository)
                .save(executionCaptor.capture());

        Execution savedExecution = executionCaptor.getValue();

        assertThat(savedExecution.getExecutedQuantity()).isEqualTo(2);
        assertThat(savedExecution.getRemainingQuantity()).isZero();
    }

    @Test
    @DisplayName("주문을 찾을 수 없으면 ORDER_NOT_FOUND 예외가 발생한다")
    void applyExecution_orderNotFound() {
        // given
        UUID orderId = UUID.randomUUID();

        when(orderCommandRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                orderExecutionCommandService.applyExecution(
                        orderId,
                        2,
                        2,
                        new BigDecimal("69800"),
                        139_600L
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                TradingErrorCode.ORDER_NOT_FOUND
                        )
                );

        verifyNoInteractions(executionCommandRepository);
    }

    @Test
    @DisplayName("체결 조회 시각을 갱신한다")
    void markExecutionChecked_success() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();
        Instant checkedAt = Instant.now();

        when(orderCommandRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        // when
        orderExecutionCommandService.markExecutionChecked(
                orderId,
                checkedAt
        );

        // then
        assertThat(order.getLastExecutionCheckedAt())
                .isEqualTo(checkedAt);

        verify(orderCommandRepository)
                .findByIdForUpdate(orderId);
    }

    @Test
    @DisplayName("부분 체결 후 나머지가 거절되면 주문을 종결하고 Execution을 생성한다")
    void applyRejection_partialFill_createExecution() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();

        when(orderCommandRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        when(executionCommandRepository.findByOrderId(orderId))
                .thenReturn(Optional.empty());

        // when
        orderExecutionCommandService.applyRejection(
                orderId,
                2,
                0,
                2,
                new BigDecimal("69800"),
                139_600L
        );

        // then
        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.PARTIALLY_FILLED_REJECTED);
        assertThat(order.getFilledQuantity()).isEqualTo(2);
        assertThat(order.getRemainingQuantity()).isZero();

        ArgumentCaptor<Execution> executionCaptor =
                ArgumentCaptor.forClass(Execution.class);

        verify(executionCommandRepository)
                .save(executionCaptor.capture());

        Execution savedExecution = executionCaptor.getValue();

        assertThat(savedExecution.getOrderId()).isEqualTo(orderId);
        assertThat(savedExecution.getExecutedQuantity()).isEqualTo(2);
        assertThat(savedExecution.getRemainingQuantity()).isZero();
        assertThat(savedExecution.getAverageExecutionPrice())
                .isEqualByComparingTo(new BigDecimal("69800"));
        assertThat(savedExecution.getTotalExecutionAmount())
                .isEqualTo(139_600L);
    }

    @Test
    @DisplayName("체결 없이 전량 거절되면 FAILED로 종결하고 Execution을 생성하지 않는다")
    void applyRejection_fullReject_withoutExecution() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();

        when(orderCommandRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        // when
        orderExecutionCommandService.applyRejection(
                orderId,
                0,
                0,
                4,
                new BigDecimal("0"),
                0L
        );

        // then
        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.FAILED);
        assertThat(order.getFilledQuantity()).isZero();
        assertThat(order.getRemainingQuantity()).isZero();

        verify(executionCommandRepository, never())
                .findByOrderId(any(UUID.class));

        verify(executionCommandRepository, never())
                .save(any(Execution.class));
    }

    private Order createAcceptedOrder() {
        Order order =
                Order.create(
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
        order.accept("0001234567");

        return order;
    }
}
