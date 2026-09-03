package com.sajo.trading_service.trading.kafka.consumer;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.kafka.dto.TradingSignalGeneratedEvent;
import com.sajo.trading_service.trading.kafka.dto.TradingSignalPayload;
import com.sajo.trading_service.trading.service.command.TradingSignalCommandService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradingSignalConsumerTest {

    @Mock
    private TradingSignalCommandService tradingSignalCommandService;

    @Mock
    private Validator validator;

    @InjectMocks
    private TradingSignalConsumer tradingSignalConsumer;

    @Test
    @DisplayName("유효한 Signal이면 서비스로 전달한다")
    void consume() {
        // given
        UUID userId = UUID.randomUUID();

        TradingSignalGeneratedEvent event =
                new TradingSignalGeneratedEvent(
                        UUID.randomUUID(),
                        "TRADING_SIGNAL_GENERATED",
                        1,
                        Instant.now(),
                        userId,
                        new TradingSignalPayload(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                userId,
                                "005930",
                                OrderType.BUY,
                                70_000L,
                                300_000L,
                                "RSI 조건 충족"
                        )
                );

        given(validator.validate(event))
                .willReturn(Set.of());

        // when
        tradingSignalConsumer.consume(event);

        // then
        verify(tradingSignalCommandService)
                .processSignal(event);
    }

    @Test
    @DisplayName("유효하지 않은 Signal이면 서비스로 전달하지 않는다")
    void invalidSignal() {
        // given
        UUID userId = UUID.randomUUID();

        TradingSignalGeneratedEvent event =
                new TradingSignalGeneratedEvent(
                        UUID.randomUUID(),
                        "TRADING_SIGNAL_GENERATED",
                        1,
                        Instant.now(),
                        userId,
                        new TradingSignalPayload(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                userId,
                                "",
                                OrderType.BUY,
                                -100L,
                                300_000L,
                                "잘못된 Signal"
                        )
                );

        given(validator.validate(event))
                .willReturn(Set.of(
                        mock(ConstraintViolation.class)
                ));

        // when & then
        assertThatThrownBy(() ->
                tradingSignalConsumer.consume(event)
        ).isInstanceOf(BusinessException.class);

        verify(tradingSignalCommandService, never())
                .processSignal(any());
    }

    @Test
    void consume_shouldSkipWhenAutoTradingDisabled() {
        // given
        TradingSignalGeneratedEvent event = createValidEvent();

        when(validator.validate(event))
                .thenReturn(Set.of());

        doThrow(new BusinessException(
                TradingErrorCode.AUTO_TRADING_DISABLED
        )).when(tradingSignalCommandService)
                .processSignal(event);

        // when & then
        assertThatCode(() -> tradingSignalConsumer.consume(event))
                .doesNotThrowAnyException();

        verify(tradingSignalCommandService).processSignal(event);
    }

    @Test
    void consume_shouldRethrowWhenTradingLimitNotFound() {
        // given
        TradingSignalGeneratedEvent event = createValidEvent();

        when(validator.validate(event))
                .thenReturn(Set.of());

        doThrow(new BusinessException(
                TradingErrorCode.TRADING_LIMIT_NOT_FOUND
        )).when(tradingSignalCommandService)
                .processSignal(event);

        // when & then
        assertThatThrownBy(() -> tradingSignalConsumer.consume(event))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception).getErrorCode()
                        ).isEqualTo(
                                TradingErrorCode.TRADING_LIMIT_NOT_FOUND
                        )
                );

        verify(tradingSignalCommandService).processSignal(event);
    }

    private TradingSignalGeneratedEvent createValidEvent() {
        UUID userId = UUID.randomUUID();

        return new TradingSignalGeneratedEvent(
                UUID.randomUUID(),
                "TRADING_SIGNAL_GENERATED",
                1,
                Instant.now(),
                userId,
                new TradingSignalPayload(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        userId,
                        "005930",
                        OrderType.BUY,
                        70_000L,
                        300_000L,
                        "RSI 조건 충족"
                )
        );
    }
}