package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.feign.FeignApiException;
import com.sajo.trading_service.trading.client.AccountClient;
import com.sajo.trading_service.trading.client.KisOrderClient;
import com.sajo.trading_service.trading.client.dto.request.KisOrderRequest;
import com.sajo.trading_service.trading.client.dto.response.*;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.AccountType;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import feign.FeignException;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KisOrderCommandServiceTest {

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
    @DisplayName("BUY 주문 성공 시 PROCESSING 선점 후 ACCEPTED 처리한다")
    void executeBuyOrderSuccess() {
        // given
        Order order = createOrder(OrderType.BUY);

        when(orderStatusCommandService.startProcessing(orderId))
                .thenReturn(order);

        givenCommonAccountResponses();

        when(accountClient.getOrderableAmount(userId))
                .thenReturn(
                        new AccountOrderableAmountResponse(
                                1_000_000L
                        )
                );

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
                .startProcessing(orderId);

        verify(orderStatusCommandService)
                .accept(
                        orderId,
                        "1234567890"
                );

        verify(orderStatusCommandService, never())
                .fail(
                        any(),
                        any(),
                        any()
                );

        verify(orderStatusCommandService, never())
                .timeout(
                        any(),
                        any(),
                        any()
                );
    }

    @Test
    @DisplayName("BUY 주문 가능 금액이 부족하면 FAILED 처리하고 KIS를 호출하지 않는다")
    void executeBuyOrderNotEnoughAmount() {
        // given
        Order order = createOrder(OrderType.BUY);

        when(orderStatusCommandService.startProcessing(orderId))
                .thenReturn(order);

        givenCommonAccountResponses();

        when(accountClient.getOrderableAmount(userId))
                .thenReturn(
                        new AccountOrderableAmountResponse(
                                100_000L
                        )
                );

        // when
        kisOrderCommandService.executeOrder(orderId);

        // then
        verify(orderStatusCommandService)
                .startProcessing(orderId);

        verify(orderStatusCommandService)
                .fail(
                        orderId,
                        "ORDERABLE_AMOUNT_NOT_ENOUGH",
                        "주문 가능 금액이 부족합니다."
                );

        verifyNoInteractions(kisOrderClient);

        verify(orderStatusCommandService, never())
                .accept(
                        any(),
                        any()
                );

        verify(orderStatusCommandService, never())
                .timeout(
                        any(),
                        any(),
                        any()
                );
    }

    @Test
    @DisplayName("BUY 주문 가능 금액이 null이면 FAILED 처리하고 KIS를 호출하지 않는다")
    void executeBuyOrderNullOrderableAmount() {
        // given
        Order order = createOrder(OrderType.BUY);

        when(orderStatusCommandService.startProcessing(orderId))
                .thenReturn(order);

        givenCommonAccountResponses();

        when(accountClient.getOrderableAmount(userId))
                .thenReturn(
                        new AccountOrderableAmountResponse(
                                null
                        )
                );

        // when
        kisOrderCommandService.executeOrder(orderId);

        // then
        verify(orderStatusCommandService)
                .startProcessing(orderId);

        verify(orderStatusCommandService)
                .fail(
                        orderId,
                        "ORDERABLE_AMOUNT_NOT_ENOUGH",
                        "주문 가능 금액이 부족합니다."
                );

        verifyNoInteractions(kisOrderClient);
    }

    @Test
    @DisplayName("SELL 매도 가능 수량이 부족하면 FAILED 처리하고 KIS를 호출하지 않는다")
    void executeSellOrderNotEnoughQuantity() {
        // given
        Order order = createOrder(OrderType.SELL);

        when(orderStatusCommandService.startProcessing(orderId))
                .thenReturn(order);

        givenCommonAccountResponses();

        when(accountClient.getHolding(
                userId,
                order.getStockCode()
        )).thenReturn(
                new AccountHoldingResponse(
                        1
                )
        );

        // when
        kisOrderCommandService.executeOrder(orderId);

        // then
        verify(orderStatusCommandService)
                .startProcessing(orderId);

        verify(orderStatusCommandService)
                .fail(
                        orderId,
                        "SELLABLE_QUANTITY_NOT_ENOUGH",
                        "매도 가능 수량이 부족합니다."
                );

        verifyNoInteractions(kisOrderClient);

        verify(orderStatusCommandService, never())
                .accept(
                        any(),
                        any()
                );

        verify(orderStatusCommandService, never())
                .timeout(
                        any(),
                        any(),
                        any()
                );
    }

    @Test
    @DisplayName("SELL 매도 가능 수량이 null이면 FAILED 처리하고 KIS를 호출하지 않는다")
    void executeSellOrderNullSellableQuantity() {
        // given
        Order order = createOrder(OrderType.SELL);

        when(orderStatusCommandService.startProcessing(orderId))
                .thenReturn(order);

        givenCommonAccountResponses();

        when(accountClient.getHolding(
                userId,
                order.getStockCode()
        )).thenReturn(
                new AccountHoldingResponse(
                        null
                )
        );

        // when
        kisOrderCommandService.executeOrder(orderId);

        // then
        verify(orderStatusCommandService)
                .startProcessing(orderId);

        verify(orderStatusCommandService)
                .fail(
                        orderId,
                        "SELLABLE_QUANTITY_NOT_ENOUGH",
                        "매도 가능 수량이 부족합니다."
                );

        verifyNoInteractions(kisOrderClient);
    }

    @Test
    @DisplayName("Account Service에서 FeignApiException이 발생하면 FAILED 처리한다")
    void executeOrderAccountFeignApiException() {
        // given
        Order order = createOrder(OrderType.BUY);

        when(orderStatusCommandService.startProcessing(orderId))
                .thenReturn(order);

        FeignApiException exception =
                new FeignApiException(
                        "ACCOUNT_ERROR",
                        "Account Service 오류",
                        400
                );

        when(accountClient.getAccessToken(userId))
                .thenThrow(exception);

        // when
        kisOrderCommandService.executeOrder(orderId);

        // then
        verify(orderStatusCommandService)
                .startProcessing(orderId);

        verify(orderStatusCommandService)
                .fail(
                        orderId,
                        "ACCOUNT_ERROR",
                        "계좌 정보를 확인하는 중 오류가 발생했습니다."
                );

        verifyNoInteractions(kisOrderClient);
    }

    @Test
    @DisplayName("KIS가 주문 실패 응답을 반환하면 FAILED 처리한다")
    void executeOrderFailResponse() {
        // given
        Order order = createOrder(OrderType.BUY);

        when(orderStatusCommandService.startProcessing(orderId))
                .thenReturn(order);

        givenCommonAccountResponses();

        when(accountClient.getOrderableAmount(userId))
                .thenReturn(
                        new AccountOrderableAmountResponse(
                                1_000_000L
                        )
                );

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
                .startProcessing(orderId);

        verify(orderStatusCommandService)
                .fail(
                        orderId,
                        "KIS_ERROR",
                        "주문 실패"
                );

        verify(orderStatusCommandService, never())
                .accept(
                        any(),
                        any()
                );

        verify(orderStatusCommandService, never())
                .timeout(
                        any(),
                        any(),
                        any()
                );
    }

    @Test
    @DisplayName("KIS 성공 응답에 output이 없으면 TIMEOUT 처리한다")
    void executeOrderSuccessWithoutOutput() {
        // given
        Order order = createOrder(OrderType.BUY);

        when(orderStatusCommandService.startProcessing(orderId))
                .thenReturn(order);

        givenCommonAccountResponses();

        when(accountClient.getOrderableAmount(userId))
                .thenReturn(
                        new AccountOrderableAmountResponse(
                                1_000_000L
                        )
                );

        KisOrderResponse response =
                new KisOrderResponse(
                        "0",
                        "SUCCESS",
                        "주문 전송 완료",
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
                .timeout(
                        orderId,
                        "KIS_INVALID_RESPONSE",
                        "KIS 주문 성공 응답의 주문번호를 확인할 수 없습니다."
                );

        verify(orderStatusCommandService, never())
                .accept(
                        any(),
                        any()
                );
    }

    @Test
    @DisplayName("KIS 성공 응답의 주문번호가 비어있으면 TIMEOUT 처리한다")
    void executeOrderSuccessWithoutOrderNumber() {
        // given
        Order order = createOrder(OrderType.BUY);

        when(orderStatusCommandService.startProcessing(orderId))
                .thenReturn(order);

        givenCommonAccountResponses();

        when(accountClient.getOrderableAmount(userId))
                .thenReturn(
                        new AccountOrderableAmountResponse(
                                1_000_000L
                        )
                );

        KisOrderResponse response =
                new KisOrderResponse(
                        "0",
                        "SUCCESS",
                        "주문 전송 완료",
                        new KisOrderResponse.KisOrderOutput(
                                "",
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
                .timeout(
                        orderId,
                        "KIS_INVALID_RESPONSE",
                        "KIS 주문 성공 응답의 주문번호를 확인할 수 없습니다."
                );

        verify(orderStatusCommandService, never())
                .accept(
                        any(),
                        any()
                );
    }

    @Test
    @DisplayName("KIS 호출 중 RetryableException이 발생하면 TIMEOUT 처리한다")
    void executeOrderTimeout() {
        // given
        Order order = createOrder(OrderType.BUY);

        when(orderStatusCommandService.startProcessing(orderId))
                .thenReturn(order);

        givenCommonAccountResponses();

        when(accountClient.getOrderableAmount(userId))
                .thenReturn(
                        new AccountOrderableAmountResponse(
                                1_000_000L
                        )
                );

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
                        "KIS 주문 응답을 확인할 수 없습니다."
                );

        verify(orderStatusCommandService, never())
                .accept(
                        any(),
                        any()
                );

        verify(orderStatusCommandService, never())
                .fail(
                        any(),
                        any(),
                        any()
                );
    }

    @Test
    @DisplayName("KIS가 4xx 응답을 반환하면 FAILED 처리한다")
    void executeOrderClientError() {
        // given
        Order order = createOrder(OrderType.BUY);

        when(orderStatusCommandService.startProcessing(orderId))
                .thenReturn(order);

        givenCommonAccountResponses();

        when(accountClient.getOrderableAmount(userId))
                .thenReturn(
                        new AccountOrderableAmountResponse(
                                1_000_000L
                        )
                );

        FeignException feignException =
                mock(FeignException.class);

        when(feignException.status())
                .thenReturn(400);

        when(kisOrderClient.placeOrder(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(KisOrderRequest.class)
        )).thenThrow(feignException);

        // when
        kisOrderCommandService.executeOrder(orderId);

        // then
        verify(orderStatusCommandService)
                .fail(
                        orderId,
                        "KIS_HTTP_400",
                        "KIS 주문 요청이 실패했습니다."
                );

        verify(orderStatusCommandService, never())
                .accept(
                        any(),
                        any()
                );
    }

    @Test
    @DisplayName("KIS가 429 응답을 반환하면 TIMEOUT 처리한다")
    void executeOrderRateLimited() {
        // given
        Order order = createOrder(OrderType.BUY);

        when(orderStatusCommandService.startProcessing(orderId))
                .thenReturn(order);

        givenCommonAccountResponses();

        when(accountClient.getOrderableAmount(userId))
                .thenReturn(
                        new AccountOrderableAmountResponse(
                                1_000_000L
                        )
                );

        FeignException feignException =
                mock(FeignException.class);

        when(feignException.status())
                .thenReturn(429);

        when(kisOrderClient.placeOrder(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(KisOrderRequest.class)
        )).thenThrow(feignException);

        // when
        kisOrderCommandService.executeOrder(orderId);

        // then
        verify(orderStatusCommandService)
                .timeout(
                        orderId,
                        "KIS_RETRYABLE_HTTP_429",
                        "KIS 주문 요청을 일시적으로 처리할 수 없습니다."
                );

        verify(orderStatusCommandService, never())
                .fail(
                        any(),
                        any(),
                        any()
                );
    }

    @Test
    @DisplayName("KIS가 5xx 응답을 반환하면 TIMEOUT 처리한다")
    void executeOrderServerError() {
        // given
        Order order = createOrder(OrderType.BUY);

        when(orderStatusCommandService.startProcessing(orderId))
                .thenReturn(order);

        givenCommonAccountResponses();

        when(accountClient.getOrderableAmount(userId))
                .thenReturn(
                        new AccountOrderableAmountResponse(
                                1_000_000L
                        )
                );

        FeignException feignException =
                mock(FeignException.class);

        when(feignException.status())
                .thenReturn(500);

        when(kisOrderClient.placeOrder(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(KisOrderRequest.class)
        )).thenThrow(feignException);

        // when
        kisOrderCommandService.executeOrder(orderId);

        // then
        verify(orderStatusCommandService)
                .timeout(
                        orderId,
                        "KIS_SERVER_ERROR",
                        "KIS 서버 오류로 주문 결과를 확인할 수 없습니다."
                );

        verify(orderStatusCommandService, never())
                .accept(
                        any(),
                        any()
                );
    }

    @Test
    @DisplayName("PROCESSING 선점에 실패하면 Account와 KIS를 호출하지 않는다")
    void executeOrderProcessingNotAllowed() {
        // given
        doThrow(
                new BusinessException(
                        TradingErrorCode.ORDER_EXECUTION_NOT_ALLOWED
                )
        )
                .when(orderStatusCommandService)
                .startProcessing(orderId);

        // when & then
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        kisOrderCommandService.executeOrder(orderId)
                )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        org.assertj.core.api.Assertions.assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                TradingErrorCode.ORDER_EXECUTION_NOT_ALLOWED
                        )
                );

        verify(orderStatusCommandService)
                .startProcessing(orderId);

        verifyNoInteractions(accountClient);
        verifyNoInteractions(kisOrderClient);
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
    @Test
    @DisplayName("Account Service 처리 중 예상하지 못한 예외가 발생하면 FAILED 처리한다")
    void executeOrderAccountUnexpectedError() {
        // given
        Order order = createOrder(OrderType.BUY);

        when(orderStatusCommandService.startProcessing(orderId))
                .thenReturn(order);

        when(accountClient.getAccessToken(userId))
                .thenThrow(new RuntimeException("unexpected"));

        // when
        kisOrderCommandService.executeOrder(orderId);

        // then
        verify(orderStatusCommandService)
                .fail(
                        orderId,
                        "ACCOUNT_SERVICE_UNKNOWN_ERROR",
                        "Account Service 처리 중 예상하지 못한 오류가 발생했습니다."
                );

        verifyNoInteractions(kisOrderClient);
    }

    @Test
    @DisplayName("KIS 주문 처리 중 예상하지 못한 예외가 발생하면 TIMEOUT 처리한다")
    void executeOrderKisUnexpectedError() {
        // given
        Order order = createOrder(OrderType.BUY);

        when(orderStatusCommandService.startProcessing(orderId))
                .thenReturn(order);

        givenCommonAccountResponses();

        when(accountClient.getOrderableAmount(userId))
                .thenReturn(
                        new AccountOrderableAmountResponse(
                                1_000_000L
                        )
                );

        when(kisOrderClient.placeOrder(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(KisOrderRequest.class)
        )).thenThrow(
                new RuntimeException("unexpected")
        );

        // when
        kisOrderCommandService.executeOrder(orderId);

        // then
        verify(orderStatusCommandService)
                .timeout(
                        orderId,
                        "KIS_UNKNOWN_ERROR",
                        "KIS 주문 결과를 확인할 수 없습니다."
                );

        verify(orderStatusCommandService, never())
                .accept(
                        any(),
                        any()
                );
    }

    @Test
    @DisplayName("KIS 실패 응답 후 FAILED 저장이 실패해도 TIMEOUT으로 오분류하지 않는다")
    void executeOrderFailResponseSaveFailure() {
        // given
        Order order = createOrder(OrderType.BUY);

        when(orderStatusCommandService.startProcessing(orderId))
                .thenReturn(order);

        givenCommonAccountResponses();

        when(accountClient.getOrderableAmount(userId))
                .thenReturn(
                        new AccountOrderableAmountResponse(
                                1_000_000L
                        )
                );

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

        doThrow(new RuntimeException("db error"))
                .when(orderStatusCommandService)
                .fail(
                        orderId,
                        "KIS_ERROR",
                        "주문 실패"
                );

        // when & then
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        kisOrderCommandService.executeOrder(orderId)
                )
                .isInstanceOf(RuntimeException.class);

        verify(orderStatusCommandService, never())
                .timeout(
                        any(),
                        any(),
                        any()
                );
    }
}