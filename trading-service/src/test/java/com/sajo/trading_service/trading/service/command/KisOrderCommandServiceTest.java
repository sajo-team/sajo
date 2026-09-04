package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.client.AccountClient;
import com.sajo.trading_service.trading.client.KisOrderClient;
import com.sajo.trading_service.trading.client.dto.request.KisOrderRequest;
import com.sajo.trading_service.trading.client.dto.response.*;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.AccountType;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.command.OrderCommandRepository;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KisOrderCommandServiceTest {

    @Mock
    private OrderCommandRepository orderCommandRepository;

    @Mock
    private KisOrderClient kisOrderClient;

    @Mock
    private AccountClient accountClient;

    @Mock
    private OrderStatusCommandService orderStatusCommandService;

    @InjectMocks
    private KisOrderCommandService kisOrderCommandService;

    private UUID orderId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("BUY 주문 성공 시 Order를 ACCEPTED 처리한다")
    void executeBuyOrderSuccess() {
        // given
        Order order = createOrder(OrderType.BUY);

        when(orderCommandRepository.findById(orderId))
                .thenReturn(Optional.of(order));

        givenCommonAccountResponses();

        when(accountClient.getOrderableAmount(userId))
                .thenReturn(new AccountOrderableAmountResponse(1_000_000L));

        KisOrderResponse response =
                new KisOrderResponse(
                        "0",
                        "SUCCESS",
                        "주문 전송 완료",
                        new KisOrderResponse.KisOrderOutput(
                                "1234567890",
                                "101530"
                        )
                );

        when(kisOrderClient.placeOrder(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(KisOrderRequest.class)
        )).thenReturn(response);

        // when
        kisOrderCommandService.executeOrder(orderId);

        // then
        verify(orderStatusCommandService)
                .accept(orderId, "1234567890");

        verify(orderStatusCommandService, never())
                .fail(any(), any(), any());

        verify(orderStatusCommandService, never())
                .timeout(any(), any(), any());
    }

    @Test
    @DisplayName("BUY 주문 가능 금액이 부족하면 주문을 실행하지 않는다")
    void executeBuyOrderNotEnoughAmount() {
        // given
        Order order = createOrder(OrderType.BUY);

        when(orderCommandRepository.findById(orderId))
                .thenReturn(Optional.of(order));

        givenCommonAccountResponses();

        when(accountClient.getOrderableAmount(userId))
                .thenReturn(new AccountOrderableAmountResponse(100_000L));

        // when & then
        assertThatThrownBy(() ->
                kisOrderCommandService.executeOrder(orderId)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        org.assertj.core.api.Assertions.assertThat(
                                ((BusinessException) exception).getErrorCode()
                        ).isEqualTo(
                                TradingErrorCode.ORDERABLE_AMOUNT_NOT_ENOUGH
                        )
                );

        verifyNoInteractions(kisOrderClient);
        verifyNoInteractions(orderStatusCommandService);
    }

    @Test
    @DisplayName("SELL 매도 가능 수량이 부족하면 주문을 실행하지 않는다")
    void executeSellOrderNotEnoughQuantity() {
        // given
        Order order = createOrder(OrderType.SELL);

        when(orderCommandRepository.findById(orderId))
                .thenReturn(Optional.of(order));

        givenCommonAccountResponses();

        when(accountClient.getHolding(
                userId,
                order.getStockCode()
        )).thenReturn(
                new AccountHoldingResponse(1)
        );

        // when & then
        assertThatThrownBy(() ->
                kisOrderCommandService.executeOrder(orderId)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        org.assertj.core.api.Assertions.assertThat(
                                ((BusinessException) exception).getErrorCode()
                        ).isEqualTo(
                                TradingErrorCode.SELLABLE_QUANTITY_NOT_ENOUGH
                        )
                );

        verifyNoInteractions(kisOrderClient);
        verifyNoInteractions(orderStatusCommandService);
    }

    @Test
    @DisplayName("KIS가 주문 실패 응답을 반환하면 Order를 FAILED 처리한다")
    void executeOrderFailResponse() {
        // given
        Order order = createOrder(OrderType.BUY);

        when(orderCommandRepository.findById(orderId))
                .thenReturn(Optional.of(order));

        givenCommonAccountResponses();

        when(accountClient.getOrderableAmount(userId))
                .thenReturn(new AccountOrderableAmountResponse(1_000_000L));

        KisOrderResponse response =
                new KisOrderResponse(
                        "1",
                        "KIS_ERROR",
                        "주문 실패",
                        null
                );

        when(kisOrderClient.placeOrder(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(KisOrderRequest.class)
        )).thenReturn(response);

        // when
        kisOrderCommandService.executeOrder(orderId);

        // then
        verify(orderStatusCommandService)
                .fail(
                        orderId,
                        "KIS_ERROR",
                        "주문 실패"
                );

        verify(orderStatusCommandService, never())
                .accept(any(), any());

        verify(orderStatusCommandService, never())
                .timeout(any(), any(), any());
    }

    @Test
    @DisplayName("KIS 호출 중 RetryableException이 발생하면 TIMEOUT 처리한다")
    void executeOrderTimeout() {
        // given
        Order order = createOrder(OrderType.BUY);

        when(orderCommandRepository.findById(orderId))
                .thenReturn(Optional.of(order));

        givenCommonAccountResponses();

        when(accountClient.getOrderableAmount(userId))
                .thenReturn(new AccountOrderableAmountResponse(1_000_000L));

        RetryableException retryableException =
                mock(RetryableException.class);

        when(kisOrderClient.placeOrder(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(KisOrderRequest.class)
        )).thenThrow(retryableException);

        // when
        kisOrderCommandService.executeOrder(orderId);

        // then
        verify(orderStatusCommandService)
                .timeout(
                        orderId,
                        "KIS_TIMEOUT",
                        "KIS 주문 응답 TIMEOUT 발생"
                );

        verify(orderStatusCommandService, never())
                .accept(any(), any());

        verify(orderStatusCommandService, never())
                .fail(any(), any(), any());
    }

    private Order createOrder(OrderType orderType) {
        return Order.create(
                userId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "005930",
                orderType,
                70_000L,
                4
        );
    }

    private void givenCommonAccountResponses() {
        when(accountClient.getAccessToken(userId))
                .thenReturn(
                        new AccountTokenResponse(
                                "access-token",
                                "app-key",
                                "secret-key"
                        )
                );

        when(accountClient.getOrderInfo(userId))
                .thenReturn(
                        new AccountOrderInfoResponse(
                                "12345678-01",
                                AccountType.VIRTUAL,
                                "12345678",
                                "01"
                        )
                );
    }
}