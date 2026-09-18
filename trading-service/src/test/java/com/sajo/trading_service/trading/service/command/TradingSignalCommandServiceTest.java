package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.domain.AutoTradingOperationControl;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.TradingLimit;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.event.OrderRequestedEvent;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.kafka.dto.TradingSignalGeneratedEvent;
import com.sajo.trading_service.trading.kafka.dto.TradingSignalPayload;
import com.sajo.trading_service.trading.repository.command.AutoTradingCommandRepository;
import com.sajo.trading_service.trading.repository.command.AutoTradingOperationControlCommandRepository;
import com.sajo.trading_service.trading.repository.command.OrderCommandRepository;
import com.sajo.trading_service.trading.repository.command.TradingLimitCommandRepository;
import com.sajo.trading_service.trading.repository.query.OrderQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradingSignalCommandServiceTest {

    @Mock
    private OrderCommandRepository orderCommandRepository;

    @Mock
    private OrderQueryRepository orderQueryRepository;

    @Mock
    private AutoTradingCommandRepository autoTradingCommandRepository;

    @Mock
    private TradingLimitCommandRepository tradingLimitCommandRepository;

    @Mock
    private AutoTradingOperationControlCommandRepository
            autoTradingOperationControlCommandRepository;

    @InjectMocks
    private TradingSignalCommandService tradingSignalCommandService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private UUID userId;
    private UUID strategyId;
    private UUID signalId;
    private UUID autoTradingId;

    private AutoTrading autoTrading;
    private TradingLimit tradingLimit;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        strategyId = UUID.randomUUID();
        signalId = UUID.randomUUID();
        autoTradingId = UUID.randomUUID();

        autoTrading = mock(AutoTrading.class);
        tradingLimit = mock(TradingLimit.class);
    }

    @Test
    @DisplayName("정상 Signal을 수신하면 Order를 생성한다")
    void processSignalSuccess() {
        // given
        mockGlobalTradingEnabled();

        TradingSignalGeneratedEvent event =
                createEvent(300_000L, 70_000L, OrderType.BUY);

        given(orderCommandRepository.existsBySignalId(signalId))
                .willReturn(false);

        given(autoTradingCommandRepository
                .findByUserIdAndStrategyIdForUpdate(userId, strategyId))
                .willReturn(Optional.of(autoTrading));

        given(autoTrading.getId())
                .willReturn(autoTradingId);

        given(autoTrading.isTradable())
                .willReturn(true);

        given(tradingLimitCommandRepository.findByUserIdForUpdate(userId))
                .willReturn(Optional.of(tradingLimit));

        given(tradingLimit.getDailyMaxOrderCount())
                .willReturn(10);

        given(tradingLimit.getDailyMaxOrderAmount())
                .willReturn(3_000_000L);

        given(orderCommandRepository.countOrdersByUserIdAndCreatedAtBetween(
                eq(userId),
                any(),
                any(Instant.class),
                any(Instant.class)
        )).willReturn(0L);

        given(orderQueryRepository
                .existsActiveOrderByAutoTradingIdAndOrderType(
                        autoTradingId,
                        OrderType.BUY
                ))
                .willReturn(false);

        given(orderCommandRepository.sumEstimatedOrderAmountByUserIdAndCreatedAtBetween(
                eq(userId),
                any(),
                any(Instant.class),
                any(Instant.class)
        )).willReturn(0L);

        given(orderCommandRepository.save(any(Order.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        tradingSignalCommandService.processSignal(event);

        // then
        ArgumentCaptor<Order> orderCaptor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderCommandRepository)
                .save(orderCaptor.capture());

        Order savedOrder = orderCaptor.getValue();

        assertThat(savedOrder.getUserId()).isEqualTo(userId);
        assertThat(savedOrder.getAutoTradingId()).isEqualTo(autoTradingId);
        assertThat(savedOrder.getStrategyId()).isEqualTo(strategyId);
        assertThat(savedOrder.getSignalId()).isEqualTo(signalId);
        assertThat(savedOrder.getStockCode()).isEqualTo("005930");
        assertThat(savedOrder.getOrderType()).isEqualTo(OrderType.BUY);
        assertThat(savedOrder.getSignalPrice()).isEqualTo(70_000L);

        // 300,000 / 70,000 = 4주
        assertThat(savedOrder.getOrderQuantity()).isEqualTo(4);

        // 70,000 * 4 = 280,000원
        assertThat(savedOrder.getEstimatedOrderAmount())
                .isEqualTo(280_000L);

        assertThat(savedOrder.getStatus())
                .isEqualTo(OrderStatus.REQUESTED);

        verify(applicationEventPublisher)
                .publishEvent(any(OrderRequestedEvent.class));

        verify(autoTrading)
                .validateDirection(OrderType.BUY);
    }

    @Test
    @DisplayName("이미 처리된 Signal이면 Order를 생성하지 않는다")
    void duplicateSignal() {
        // given
        TradingSignalGeneratedEvent event =
                createEvent(300_000L, 70_000L, OrderType.BUY);

        given(orderCommandRepository.existsBySignalId(signalId))
                .willReturn(true);

        // when
        tradingSignalCommandService.processSignal(event);

        // then
        verify(orderCommandRepository, never())
                .save(any(Order.class));

        verifyNoInteractions(
                autoTradingCommandRepository,
                tradingLimitCommandRepository
        );
    }

    @Test
    @DisplayName("AutoTrading이 존재하지 않으면 주문을 생성할 수 없다")
    void autoTradingNotFound() {
        // given
        mockGlobalTradingEnabled();

        TradingSignalGeneratedEvent event =
                createEvent(300_000L, 70_000L, OrderType.BUY);

        given(orderCommandRepository.existsBySignalId(signalId))
                .willReturn(false);

        given(autoTradingCommandRepository
                .findByUserIdAndStrategyIdForUpdate(userId, strategyId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                tradingSignalCommandService.processSignal(event)
        ).isInstanceOf(BusinessException.class);

        verify(orderCommandRepository, never())
                .save(any(Order.class));
    }

    @Test
    @DisplayName("AutoTrading이 비활성화 상태이면 주문을 생성할 수 없다")
    void autoTradingDisabled() {
        // given
        mockGlobalTradingEnabled();

        TradingSignalGeneratedEvent event =
                createEvent(300_000L, 70_000L, OrderType.BUY);

        given(orderCommandRepository.existsBySignalId(signalId))
                .willReturn(false);

        given(autoTradingCommandRepository
                .findByUserIdAndStrategyIdForUpdate(userId, strategyId))
                .willReturn(Optional.of(autoTrading));

        given(autoTrading.isTradable())
                .willReturn(false);

        // when & then
        assertThatThrownBy(() ->
                tradingSignalCommandService.processSignal(event)
        ).isInstanceOf(BusinessException.class);

        verify(orderCommandRepository, never())
                .save(any(Order.class));
    }

    @Test
    @DisplayName("TradingLimit이 존재하지 않으면 주문을 생성할 수 없다")
    void tradingLimitNotFound() {
        // given
        mockGlobalTradingEnabled();

        TradingSignalGeneratedEvent event =
                createEvent(300_000L, 70_000L, OrderType.BUY);

        given(orderCommandRepository.existsBySignalId(signalId))
                .willReturn(false);

        given(autoTradingCommandRepository
                .findByUserIdAndStrategyIdForUpdate(userId, strategyId))
                .willReturn(Optional.of(autoTrading));

        given(autoTrading.isTradable())
                .willReturn(true);

        given(tradingLimitCommandRepository.findByUserIdForUpdate(userId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                tradingSignalCommandService.processSignal(event)
        ).isInstanceOf(BusinessException.class);

        verify(orderCommandRepository, never())
                .save(any(Order.class));
    }

    @Test
    @DisplayName("1회 주문 금액으로 한 주도 주문할 수 없으면 실패한다")
    void orderQuantityNotAvailable() {
        // given
        mockGlobalTradingEnabled();

        TradingSignalGeneratedEvent event =
                createEvent(
                        30_000L,
                        70_000L,
                        OrderType.BUY
                );

        given(orderCommandRepository.existsBySignalId(signalId))
                .willReturn(false);

        given(autoTradingCommandRepository
                .findByUserIdAndStrategyIdForUpdate(userId, strategyId))
                .willReturn(Optional.of(autoTrading));

        given(autoTrading.isTradable())
                .willReturn(true);

        given(tradingLimitCommandRepository.findByUserIdForUpdate(userId))
                .willReturn(Optional.of(tradingLimit));

        // when & then
        assertThatThrownBy(() ->
                tradingSignalCommandService.processSignal(event)
        ).isInstanceOf(BusinessException.class);

        verify(orderCommandRepository, never())
                .save(any(Order.class));
    }

    @Test
    @DisplayName("일일 최대 주문 횟수를 초과하면 주문을 생성할 수 없다")
    void dailyOrderCountExceeded() {
        // given
        TradingSignalGeneratedEvent event =
                createEvent(300_000L, 70_000L, OrderType.BUY);

        prepareValidAutoTradingAndLimit();

        given(tradingLimit.getDailyMaxOrderCount())
                .willReturn(10);

        given(orderCommandRepository.countOrdersByUserIdAndCreatedAtBetween(
                eq(userId),
                any(),
                any(Instant.class),
                any(Instant.class)
        )).willReturn(10L);

        assertThatThrownBy(() ->
                tradingSignalCommandService.processSignal(event)
        ).isInstanceOf(BusinessException.class);

        verify(orderCommandRepository, never())
                .save(any(Order.class));
    }

    @Test
    @DisplayName("일일 최대 주문 금액을 초과하면 주문을 생성할 수 없다")
    void dailyOrderAmountExceeded() {
        // given
        TradingSignalGeneratedEvent event =
                createEvent(300_000L, 70_000L, OrderType.BUY);

        prepareValidAutoTradingAndLimit();

        given(tradingLimit.getDailyMaxOrderCount())
                .willReturn(10);

        given(tradingLimit.getDailyMaxOrderAmount())
                .willReturn(1_000_000L);

        given(orderCommandRepository.countOrdersByUserIdAndCreatedAtBetween(
                eq(userId),
                any(),
                any(Instant.class),
                any(Instant.class)
        )).willReturn(1L);

        given(orderCommandRepository.sumEstimatedOrderAmountByUserIdAndCreatedAtBetween(
                eq(userId),
                any(),
                any(Instant.class),
                any(Instant.class)
        )).willReturn(800_000L);

        assertThatThrownBy(() ->
                tradingSignalCommandService.processSignal(event)
        ).isInstanceOf(BusinessException.class);

        verify(orderCommandRepository, never())
                .save(any(Order.class));
    }

    private void prepareValidAutoTradingAndLimit() {

        mockGlobalTradingEnabled();

        given(orderCommandRepository.existsBySignalId(signalId))
                .willReturn(false);

        given(autoTradingCommandRepository
                .findByUserIdAndStrategyIdForUpdate(
                        userId,
                        strategyId
                ))
                .willReturn(Optional.of(autoTrading));

        given(autoTrading.isTradable())
                .willReturn(true);

        given(tradingLimitCommandRepository
                .findByUserIdForUpdate(userId))
                .willReturn(Optional.of(tradingLimit));
    }

    @Test
    @DisplayName("SELL Signal을 수신하면 SELL Order를 생성한다")
    void processSellSignalSuccess() {
        // given
        mockGlobalTradingEnabled();

        TradingSignalGeneratedEvent event =
                createEvent(300_000L, 70_000L, OrderType.SELL);

        given(orderCommandRepository.existsBySignalId(signalId))
                .willReturn(false);

        given(autoTradingCommandRepository
                .findByUserIdAndStrategyIdForUpdate(userId, strategyId))
                .willReturn(Optional.of(autoTrading));

        given(autoTrading.getId())
                .willReturn(autoTradingId);

        given(orderQueryRepository
                .existsActiveOrderByAutoTradingIdAndOrderType(
                        autoTradingId,
                        OrderType.SELL
                ))
                .willReturn(false);

        given(autoTrading.isTradable())
                .willReturn(true);

        given(tradingLimitCommandRepository.findByUserIdForUpdate(userId))
                .willReturn(Optional.of(tradingLimit));

        given(tradingLimit.getDailyMaxOrderCount())
                .willReturn(10);

        given(tradingLimit.getDailyMaxOrderAmount())
                .willReturn(3_000_000L);

        given(orderCommandRepository.countOrdersByUserIdAndCreatedAtBetween(
                eq(userId),
                any(),
                any(Instant.class),
                any(Instant.class)
        )).willReturn(0L);

        given(orderCommandRepository.sumEstimatedOrderAmountByUserIdAndCreatedAtBetween(
                eq(userId),
                any(),
                any(Instant.class),
                any(Instant.class)
        )).willReturn(0L);

        given(orderCommandRepository.save(any(Order.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        tradingSignalCommandService.processSignal(event);

        // then
        ArgumentCaptor<Order> orderCaptor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderCommandRepository)
                .save(orderCaptor.capture());

        Order savedOrder = orderCaptor.getValue();

        assertThat(savedOrder.getOrderType())
                .isEqualTo(OrderType.SELL);

        assertThat(savedOrder.getStatus())
                .isEqualTo(OrderStatus.REQUESTED);

        verify(applicationEventPublisher)
                .publishEvent(any(OrderRequestedEvent.class));

        verify(autoTrading)
                .validateDirection(OrderType.SELL);
    }

    private TradingSignalGeneratedEvent createEvent(
            long orderAmount,
            long triggerPrice,
            OrderType orderType
    ) {
        TradingSignalPayload payload =
                new TradingSignalPayload(
                        signalId,
                        strategyId,
                        userId,
                        "005930",
                        orderType,
                        triggerPrice,
                        orderAmount,
                        "RSI 조건 충족"
                );

        return new TradingSignalGeneratedEvent(
                UUID.randomUUID(),
                "TRADING_SIGNAL_GENERATED",
                1,
                Instant.now(),
                userId,
                payload
        );
    }

    @Test
    @DisplayName("주문 수량이 Integer 범위를 초과하면 주문 생성에 실패한다")
    void orderQuantityOverflow() {
        // given
        mockGlobalTradingEnabled();

        TradingSignalGeneratedEvent event =
                createEvent(
                        Long.MAX_VALUE,
                        1L,
                        OrderType.BUY
                );

        given(orderCommandRepository.existsBySignalId(signalId))
                .willReturn(false);

        given(autoTradingCommandRepository
                .findByUserIdAndStrategyIdForUpdate(userId, strategyId))
                .willReturn(Optional.of(autoTrading));

        given(autoTrading.isTradable())
                .willReturn(true);

        given(tradingLimitCommandRepository.findByUserIdForUpdate(userId))
                .willReturn(Optional.of(tradingLimit));

        // when & then
        assertThatThrownBy(() ->
                tradingSignalCommandService.processSignal(event)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException =
                            (BusinessException) ex;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(TradingErrorCode.ORDER_QUANTITY_OUT_OF_RANGE);
                });

        verify(orderCommandRepository, never())
                .save(any(Order.class));
    }

    @Test
    @DisplayName("Signal 수신 시 AutoTrading 주문 방향을 검증한다")
    void validateAutoTradingDirection() {
        // given
        mockGlobalTradingEnabled();

        TradingSignalGeneratedEvent event =
                createEvent(
                        300_000L,
                        70_000L,
                        OrderType.BUY
                );

        given(orderCommandRepository.existsBySignalId(signalId))
                .willReturn(false);

        given(autoTradingCommandRepository
                .findByUserIdAndStrategyIdForUpdate(
                        userId,
                        strategyId
                ))
                .willReturn(Optional.of(autoTrading));

        given(autoTrading.getId())
                .willReturn(autoTradingId);

        given(autoTrading.isTradable())
                .willReturn(true);

        given(tradingLimitCommandRepository
                .findByUserIdForUpdate(userId))
                .willReturn(Optional.of(tradingLimit));

        given(tradingLimit.getDailyMaxOrderCount())
                .willReturn(10);

        given(tradingLimit.getDailyMaxOrderAmount())
                .willReturn(3_000_000L);

        given(orderCommandRepository
                .countOrdersByUserIdAndCreatedAtBetween(
                        eq(userId),
                        any(),
                        any(Instant.class),
                        any(Instant.class)
                ))
                .willReturn(0L);

        given(orderCommandRepository
                .sumEstimatedOrderAmountByUserIdAndCreatedAtBetween(
                        eq(userId),
                        any(),
                        any(Instant.class),
                        any(Instant.class)
                ))
                .willReturn(0L);

        given(orderCommandRepository.save(any(Order.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        tradingSignalCommandService.processSignal(event);

        // then
        verify(autoTrading)
                .validateDirection(OrderType.BUY);

        verify(orderCommandRepository)
                .save(any(Order.class));
    }

    @Test
    @DisplayName("자동매매에서 허용하지 않은 방향의 Signal이면 주문을 생성하지 않는다")
    void directionNotAllowed() {
        // given
        mockGlobalTradingEnabled();

        TradingSignalGeneratedEvent event =
                createEvent(
                        300_000L,
                        70_000L,
                        OrderType.SELL
                );

        given(orderCommandRepository.existsBySignalId(signalId))
                .willReturn(false);

        given(autoTradingCommandRepository
                .findByUserIdAndStrategyIdForUpdate(
                        userId,
                        strategyId
                ))
                .willReturn(Optional.of(autoTrading));

        given(autoTrading.isTradable())
                .willReturn(true);

        doThrow(
                new BusinessException(
                        TradingErrorCode.AUTO_TRADING_DIRECTION_NOT_ALLOWED
                )
        )
                .when(autoTrading)
                .validateDirection(OrderType.SELL);

        // when & then
        assertThatThrownBy(() ->
                tradingSignalCommandService.processSignal(event)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    TradingErrorCode.AUTO_TRADING_DIRECTION_NOT_ALLOWED
                            );
                });

        verify(autoTrading)
                .validateDirection(OrderType.SELL);

        verifyNoInteractions(tradingLimitCommandRepository);

        verify(orderCommandRepository, never())
                .save(any(Order.class));

        verify(applicationEventPublisher, never())
                .publishEvent(any(OrderRequestedEvent.class));
    }

    @Test
    @DisplayName("동일 방향의 진행 중 주문이 존재하면 신규 Order를 생성하지 않는다")
    void activeOrderExists_skipNewOrder() {
        // given
        mockGlobalTradingEnabled();

        TradingSignalGeneratedEvent event =
                createEvent(300_000L, 70_000L, OrderType.BUY);

        given(orderCommandRepository.existsBySignalId(signalId))
                .willReturn(false);

        given(autoTradingCommandRepository
                .findByUserIdAndStrategyIdForUpdate(userId, strategyId))
                .willReturn(Optional.of(autoTrading));

        given(autoTrading.getId())
                .willReturn(autoTradingId);

        given(autoTrading.isTradable())
                .willReturn(true);

        given(orderQueryRepository.existsActiveOrderByAutoTradingIdAndOrderType(
                autoTradingId,
                OrderType.BUY
        )).willReturn(true);

        // when
        tradingSignalCommandService.processSignal(event);

        // then
        verify(orderCommandRepository, never())
                .save(any(Order.class));

        verify(applicationEventPublisher, never())
                .publishEvent(any(OrderRequestedEvent.class));

        verifyNoInteractions(tradingLimitCommandRepository);
    }

    @Test
    @DisplayName("반대 방향의 진행 중 주문만 존재하면 신규 Order를 생성할 수 있다")
    void oppositeDirectionActiveOrder_allowsNewOrder() {
        // given
        mockGlobalTradingEnabled();

        TradingSignalGeneratedEvent event =
                createEvent(
                        300_000L,
                        70_000L,
                        OrderType.BUY
                );

        given(orderCommandRepository.existsBySignalId(signalId))
                .willReturn(false);

        given(autoTradingCommandRepository
                .findByUserIdAndStrategyIdForUpdate(
                        userId,
                        strategyId
                ))
                .willReturn(Optional.of(autoTrading));

        given(autoTrading.getId())
                .willReturn(autoTradingId);

        given(autoTrading.isTradable())
                .willReturn(true);

        /*
         * 현재 들어온 Signal은 BUY이므로
         * BUY 방향의 진행 중 주문만 확인한다.
         *
         * SELL 주문이 진행 중이더라도
         * BUY 진행 주문이 없다면 신규 BUY Order 생성 가능.
         */
        given(orderQueryRepository
                .existsActiveOrderByAutoTradingIdAndOrderType(
                        autoTradingId,
                        OrderType.BUY
                ))
                .willReturn(false);

        given(tradingLimitCommandRepository
                .findByUserIdForUpdate(userId))
                .willReturn(Optional.of(tradingLimit));

        given(tradingLimit.getDailyMaxOrderCount())
                .willReturn(10);

        given(tradingLimit.getDailyMaxOrderAmount())
                .willReturn(3_000_000L);

        given(orderCommandRepository
                .countOrdersByUserIdAndCreatedAtBetween(
                        eq(userId),
                        any(),
                        any(Instant.class),
                        any(Instant.class)
                ))
                .willReturn(0L);

        given(orderCommandRepository
                .sumEstimatedOrderAmountByUserIdAndCreatedAtBetween(
                        eq(userId),
                        any(),
                        any(Instant.class),
                        any(Instant.class)
                ))
                .willReturn(0L);

        given(orderCommandRepository.save(any(Order.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        tradingSignalCommandService.processSignal(event);

        // then
        verify(orderQueryRepository)
                .existsActiveOrderByAutoTradingIdAndOrderType(
                        autoTradingId,
                        OrderType.BUY
                );

        verify(orderCommandRepository)
                .save(any(Order.class));

        verify(applicationEventPublisher)
                .publishEvent(any(OrderRequestedEvent.class));
    }

    @Test
    @DisplayName("전체 AutoTrading 긴급 중지 상태이면 Signal로 주문을 생성하지 않는다")
    void processSignal_globalSuspended() {
        // given
        TradingSignalGeneratedEvent event =
                createEvent(
                        300_000L,
                        70_000L,
                        OrderType.BUY
                );

        AutoTradingOperationControl control =
                mock(AutoTradingOperationControl.class);

        given(orderCommandRepository.existsBySignalId(signalId))
                .willReturn(false);

        given(autoTradingOperationControlCommandRepository
                .findByIdAndDeletedAtIsNull(
                        AutoTradingOperationControl.GLOBAL_CONTROL_ID
                ))
                .willReturn(Optional.of(control));

        given(control.isSuspended())
                .willReturn(true);

        // when
        tradingSignalCommandService.processSignal(event);

        // then
        verify(autoTradingOperationControlCommandRepository)
                .findByIdAndDeletedAtIsNull(
                        AutoTradingOperationControl.GLOBAL_CONTROL_ID
                );

        verify(autoTradingCommandRepository, never())
                .findByUserIdAndStrategyIdForUpdate(
                        any(UUID.class),
                        any(UUID.class)
                );

        verifyNoInteractions(tradingLimitCommandRepository);

        verify(orderCommandRepository, never())
                .save(any(Order.class));

        verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    @DisplayName("전체 AutoTrading 운영 제어 정보가 없으면 Signal 처리를 실패한다")
    void processSignal_globalControlNotFound() {
        // given
        TradingSignalGeneratedEvent event =
                createEvent(
                        300_000L,
                        70_000L,
                        OrderType.BUY
                );

        given(orderCommandRepository.existsBySignalId(signalId))
                .willReturn(false);

        given(autoTradingOperationControlCommandRepository
                .findByIdAndDeletedAtIsNull(
                        AutoTradingOperationControl.GLOBAL_CONTROL_ID
                ))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                tradingSignalCommandService.processSignal(event)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    TradingErrorCode.AUTO_TRADING_OPERATION_CONTROL_NOT_FOUND
                            );
                });

        verify(orderCommandRepository, never())
                .save(any(Order.class));

        verifyNoInteractions(autoTradingCommandRepository);
        verifyNoInteractions(tradingLimitCommandRepository);
        verifyNoInteractions(applicationEventPublisher);
    }

    private void mockGlobalTradingEnabled() {
        AutoTradingOperationControl control =
                mock(AutoTradingOperationControl.class);

        given(autoTradingOperationControlCommandRepository
                .findByIdAndDeletedAtIsNull(
                        AutoTradingOperationControl.GLOBAL_CONTROL_ID
                ))
                .willReturn(Optional.of(control));

        given(control.isSuspended())
                .willReturn(false);
    }
}