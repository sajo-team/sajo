package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.TradingLimit;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.kafka.dto.TradingSignalGeneratedEvent;
import com.sajo.trading_service.trading.kafka.dto.TradingSignalPayload;
import com.sajo.trading_service.trading.repository.command.AutoTradingCommandRepository;
import com.sajo.trading_service.trading.repository.command.OrderCommandRepository;
import com.sajo.trading_service.trading.repository.command.TradingLimitCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private AutoTradingCommandRepository autoTradingCommandRepository;

    @Mock
    private TradingLimitCommandRepository tradingLimitCommandRepository;

    @InjectMocks
    private TradingSignalCommandService tradingSignalCommandService;

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
        TradingSignalGeneratedEvent event =
                createEvent(300_000L, 70_000L, OrderType.BUY);

        given(orderCommandRepository.existsBySignalId(signalId))
                .willReturn(false);

        given(autoTradingCommandRepository
                .findByUserIdAndStrategyIdAndDeletedAtIsNull(userId, strategyId))
                .willReturn(Optional.of(autoTrading));

        given(autoTrading.getId())
                .willReturn(autoTradingId);

        given(autoTrading.getEnabled())
                .willReturn(true);

        given(tradingLimitCommandRepository.findByUserId(userId))
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
        TradingSignalGeneratedEvent event =
                createEvent(300_000L, 70_000L, OrderType.BUY);

        given(orderCommandRepository.existsBySignalId(signalId))
                .willReturn(false);

        given(autoTradingCommandRepository
                .findByUserIdAndStrategyIdAndDeletedAtIsNull(userId, strategyId))
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
        TradingSignalGeneratedEvent event =
                createEvent(300_000L, 70_000L, OrderType.BUY);

        given(orderCommandRepository.existsBySignalId(signalId))
                .willReturn(false);

        given(autoTradingCommandRepository
                .findByUserIdAndStrategyIdAndDeletedAtIsNull(userId, strategyId))
                .willReturn(Optional.of(autoTrading));

        given(autoTrading.getEnabled())
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
        TradingSignalGeneratedEvent event =
                createEvent(300_000L, 70_000L, OrderType.BUY);

        given(orderCommandRepository.existsBySignalId(signalId))
                .willReturn(false);

        given(autoTradingCommandRepository
                .findByUserIdAndStrategyIdAndDeletedAtIsNull(userId, strategyId))
                .willReturn(Optional.of(autoTrading));

        given(autoTrading.getEnabled())
                .willReturn(true);

        given(tradingLimitCommandRepository.findByUserId(userId))
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
        TradingSignalGeneratedEvent event =
                createEvent(
                        30_000L,
                        70_000L,
                        OrderType.BUY
                );

        given(orderCommandRepository.existsBySignalId(signalId))
                .willReturn(false);

        given(autoTradingCommandRepository
                .findByUserIdAndStrategyIdAndDeletedAtIsNull(userId, strategyId))
                .willReturn(Optional.of(autoTrading));

        given(autoTrading.getEnabled())
                .willReturn(true);

        given(tradingLimitCommandRepository.findByUserId(userId))
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

        // 이미 오늘 10번 주문
        given(orderCommandRepository.countOrdersByUserIdAndCreatedAtBetween(
                eq(userId),
                any(),
                any(Instant.class),
                any(Instant.class)
        )).willReturn(10L);

        // when & then
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
        // 이번 주문 예상 금액 = 70,000 * 4 = 280,000원
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

        // 현재 800,000원 + 이번 주문 280,000원 = 1,080,000원
        given(orderCommandRepository.sumEstimatedOrderAmountByUserIdAndCreatedAtBetween(
                eq(userId),
                any(),
                any(Instant.class),
                any(Instant.class)
        )).willReturn(800_000L);

        // when & then
        assertThatThrownBy(() ->
                tradingSignalCommandService.processSignal(event)
        ).isInstanceOf(BusinessException.class);

        verify(orderCommandRepository, never())
                .save(any(Order.class));
    }

    private void prepareValidAutoTradingAndLimit() {
        given(orderCommandRepository.existsBySignalId(signalId))
                .willReturn(false);

        given(autoTradingCommandRepository
                .findByUserIdAndStrategyIdAndDeletedAtIsNull(userId, strategyId))
                .willReturn(Optional.of(autoTrading));

        given(autoTrading.getEnabled())
                .willReturn(true);

        given(tradingLimitCommandRepository.findByUserId(userId))
                .willReturn(Optional.of(tradingLimit));
    }

    @Test
    @DisplayName("SELL Signal을 수신하면 SELL Order를 생성한다")
    void processSellSignalSuccess() {
        // given
        TradingSignalGeneratedEvent event =
                createEvent(300_000L, 70_000L, OrderType.SELL);

        given(orderCommandRepository.existsBySignalId(signalId))
                .willReturn(false);

        given(autoTradingCommandRepository
                .findByUserIdAndStrategyIdAndDeletedAtIsNull(userId, strategyId))
                .willReturn(Optional.of(autoTrading));

        given(autoTrading.getId())
                .willReturn(autoTradingId);

        given(autoTrading.getEnabled())
                .willReturn(true);

        given(tradingLimitCommandRepository.findByUserId(userId))
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
}