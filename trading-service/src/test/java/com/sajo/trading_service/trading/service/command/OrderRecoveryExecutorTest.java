package com.sajo.trading_service.trading.service.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderRecoveryExecutorTest {

    @Mock
    private KisOrderCommandService kisOrderCommandService;

    @InjectMocks
    private OrderRecoveryExecutor orderRecoveryExecutor;

    @Test
    @DisplayName("REQUESTED 주문 복구 실행 중 예외가 발생해도 예외를 전파하지 않는다")
    void executeRecoveryFailure() {
        // given
        UUID orderId = UUID.randomUUID();

        doThrow(new RuntimeException("unexpected"))
                .when(kisOrderCommandService)
                .executeOrder(orderId);

        // when & then
        assertThatCode(() ->
                orderRecoveryExecutor.execute(orderId)
        ).doesNotThrowAnyException();

        verify(kisOrderCommandService)
                .executeOrder(orderId);
    }
}