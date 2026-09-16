package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.client.AccountClient;
import com.sajo.trading_service.trading.client.KisOrderClient;
import com.sajo.trading_service.trading.client.dto.response.AccountOrderInfoResponse;
import com.sajo.trading_service.trading.client.dto.response.AccountTokenResponse;
import com.sajo.trading_service.trading.client.dto.response.KisOrderInquiryItem;
import com.sajo.trading_service.trading.client.dto.response.KisOrderInquiryResponse;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.reconciliation.KisOrderMatcher;
import com.sajo.trading_service.trading.reconciliation.MatchResult;
import com.sajo.trading_service.trading.repository.query.OrderQueryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KisOrderReconciliationServiceTest {

    @Mock
    private OrderQueryRepository orderQueryRepository;

    @Mock
    private AccountClient accountClient;

    @Mock
    private KisOrderClient kisOrderClient;

    @Mock
    private OrderStatusCommandService orderStatusCommandService;

    @Mock
    private KisOrderMatcher kisOrderMatcher;

    @Mock
    private OrderExecutionCommandService orderExecutionCommandService;

    @InjectMocks
    private KisOrderReconciliationService kisOrderReconciliationService;

    @Test
    @DisplayName("전체 주문 수량이 거절되면 FAILED로 보정한다")
    void reconcileMatchedOrder_fullRejected_fail() {
        // given
        UUID orderId = UUID.randomUUID();

        KisOrderInquiryItem item =
                createItem(
                        "0001234567",
                        "10",
                        "10"
                );

        // when
        kisOrderReconciliationService.reconcileMatchedOrder(
                orderId,
                item
        );

        // then
        verify(orderStatusCommandService).fail(
                orderId,
                "KIS_ORDER_REJECTED",
                "KIS에서 주문이 거절되었습니다."
        );

        verify(orderStatusCommandService, never())
                .accept(any(), any());
    }

    @Test
    @DisplayName("전체 거절이 아니고 주문번호가 존재하면 ACCEPTED로 보정한다")
    void reconcileMatchedOrder_orderAccepted_accept() {
        // given
        UUID orderId = UUID.randomUUID();

        KisOrderInquiryItem item =
                createItem(
                        "0001234567",
                        "10",
                        "0"
                );

        // when
        kisOrderReconciliationService.reconcileMatchedOrder(
                orderId,
                item
        );

        // then
        verify(orderStatusCommandService).accept(
                orderId,
                "0001234567"
        );

        verify(orderStatusCommandService, never())
                .fail(any(), any(), any());
    }

    @Test
    @DisplayName("일부 수량이 거절되어도 주문번호가 존재하면 ACCEPTED로 보정한다")
    void reconcileMatchedOrder_partialRejected_accept() {
        // given
        UUID orderId = UUID.randomUUID();

        KisOrderInquiryItem item =
                createItem(
                        "0001234567",
                        "10",
                        "2"
                );

        // when
        kisOrderReconciliationService.reconcileMatchedOrder(
                orderId,
                item
        );

        // then
        verify(orderStatusCommandService).accept(
                orderId,
                "0001234567"
        );

        verify(orderStatusCommandService, never())
                .fail(any(), any(), any());
    }

    @Test
    @DisplayName("주문 수량을 파싱할 수 없으면 기존 상태를 유지한다")
    void reconcileMatchedOrder_invalidQuantity_keepStatus() {
        // given
        UUID orderId = UUID.randomUUID();

        KisOrderInquiryItem item =
                createItem(
                        "0001234567",
                        "invalid",
                        "0"
                );

        // when
        kisOrderReconciliationService.reconcileMatchedOrder(
                orderId,
                item
        );

        // then
        verify(orderStatusCommandService)
                .recordReconciliationFailure(orderId);

        verify(orderStatusCommandService, never())
                .accept(any(), any());

        verify(orderStatusCommandService, never())
                .fail(any(), any(), any());
    }

    @Test
    @DisplayName("전체 거절이 아니고 주문번호도 없으면 기존 상태를 유지한다")
    void reconcileMatchedOrder_withoutOrderNo_keepStatus() {
        // given
        UUID orderId = UUID.randomUUID();

        KisOrderInquiryItem item =
                createItem(
                        null,
                        "10",
                        "0"
                );

        // when
        kisOrderReconciliationService.reconcileMatchedOrder(
                orderId,
                item
        );

        // then
        verify(orderStatusCommandService)
                .recordReconciliationFailure(orderId);

        verify(orderStatusCommandService, never())
                .accept(any(), any());

        verify(orderStatusCommandService, never())
                .fail(any(), any(), any());
    }

    @Test
    @DisplayName("PROCESSING 또는 TIMEOUT이 아닌 주문은 보정하지 않는다")
    void reconcile_notTargetStatus_skip() {
        // given
        UUID orderId = UUID.randomUUID();

        Order order = createOrder();

        ReflectionTestUtils.setField(
                order,
                "status",
                OrderStatus.ACCEPTED
        );

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        // when
        kisOrderReconciliationService.reconcile(orderId);

        // then
        verifyNoInteractions(accountClient);
        verifyNoInteractions(kisOrderClient);
        verifyNoInteractions(orderStatusCommandService);
        verifyNoInteractions(kisOrderMatcher);
    }

    @Test
    @DisplayName("KIS 주문 조회 결과가 매칭되면 ACCEPTED로 보정한다")
    void reconcile_matched_accept() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createProcessingOrder();

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        mockAccountResponses(order);

        KisOrderInquiryItem item =
                createItem(
                        "0001234567",
                        "7",
                        "0"
                );

        KisOrderInquiryResponse response =
                new KisOrderInquiryResponse(
                        "0",
                        "MCA00000",
                        "정상 처리되었습니다.",
                        "",
                        "",
                        List.of(item)
                );

        when(kisOrderClient.inquireDailyOrders(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(response);

        when(kisOrderMatcher.match(order, response.output1()))
                .thenReturn(MatchResult.matched(item));

        // when
        kisOrderReconciliationService.reconcile(orderId);

        // then
        verify(orderStatusCommandService).accept(
                orderId,
                "0001234567"
        );
    }

    @Test
    @DisplayName("KIS 주문 조회 결과가 매칭되지 않으면 기존 상태를 유지한다")
    void reconcile_notFound_keepStatus() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createProcessingOrder();

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        mockAccountResponses(order);

        KisOrderInquiryResponse response =
                new KisOrderInquiryResponse(
                        "0",
                        "MCA00000",
                        "정상 처리되었습니다.",
                        "",
                        "",
                        List.of()
                );

        when(kisOrderClient.inquireDailyOrders(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(response);

        when(kisOrderMatcher.match(order, response.output1()))
                .thenReturn(MatchResult.notFound());

        // when
        kisOrderReconciliationService.reconcile(orderId);

        // then
        verify(orderStatusCommandService)
                .recordReconciliationFailure(orderId);

        verify(orderStatusCommandService, never())
                .accept(any(), any());

        verify(orderStatusCommandService, never())
                .fail(any(), any(), any());
    }

    @Test
    @DisplayName("KIS 주문 조회 결과가 여러 건 매칭되면 기존 상태를 유지한다")
    void reconcile_ambiguous_keepStatus() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createProcessingOrder();

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        mockAccountResponses(order);

        KisOrderInquiryItem first =
                createItem(
                        "0001111111",
                        "7",
                        "0"
                );

        KisOrderInquiryItem second =
                createItem(
                        "0002222222",
                        "7",
                        "0"
                );

        KisOrderInquiryResponse response =
                new KisOrderInquiryResponse(
                        "0",
                        "MCA00000",
                        "정상 처리되었습니다.",
                        "",
                        "",
                        List.of(first, second)
                );

        when(kisOrderClient.inquireDailyOrders(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(response);

        when(kisOrderMatcher.match(order, response.output1()))
                .thenReturn(MatchResult.ambiguous());

        // when
        kisOrderReconciliationService.reconcile(orderId);

        // then
        verify(orderStatusCommandService)
                .recordReconciliationFailure(orderId);

        verify(orderStatusCommandService, never())
                .accept(any(), any());

        verify(orderStatusCommandService, never())
                .fail(any(), any(), any());
    }

    @Test
    @DisplayName("KIS 주문 조회 예외는 주문 보정 실패 횟수에 포함하지 않는다")
    void reconcile_kisError_keepStatus() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createProcessingOrder();

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        mockAccountResponses(order);

        when(kisOrderClient.inquireDailyOrders(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        )).thenThrow(new RuntimeException("KIS 조회 오류"));

        // when
        kisOrderReconciliationService.reconcile(orderId);

        // then
        verify(orderStatusCommandService, never())
                .recordReconciliationFailure(any());

        verifyNoInteractions(kisOrderMatcher);

        verify(orderStatusCommandService, never())
                .accept(any(), any());

        verify(orderStatusCommandService, never())
                .fail(any(), any(), any());
    }

    @Test
    @DisplayName("TIMEOUT 주문도 KIS 조회 결과가 매칭되면 ACCEPTED로 보정한다")
    void reconcile_timeoutMatched_accept() {
        // given
        UUID orderId = UUID.randomUUID();

        Order order = createProcessingOrder();

        ReflectionTestUtils.setField(
                order,
                "status",
                OrderStatus.TIMEOUT
        );

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        mockAccountResponses(order);

        KisOrderInquiryItem item =
                createItem(
                        "0001234567",
                        "7",
                        "0"
                );

        KisOrderInquiryResponse response =
                new KisOrderInquiryResponse(
                        "0",
                        "MCA00000",
                        "정상 처리되었습니다.",
                        "",
                        "",
                        List.of(item)
                );

        when(kisOrderClient.inquireDailyOrders(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(response);

        when(kisOrderMatcher.match(order, response.output1()))
                .thenReturn(MatchResult.matched(item));

        // when
        kisOrderReconciliationService.reconcile(orderId);

        // then
        verify(orderStatusCommandService).accept(
                orderId,
                "0001234567"
        );
    }

    @Test
    @DisplayName("KIS 주문 조회 실패 응답은 주문 보정 실패 횟수에 포함하지 않는다")
    void reconcile_kisFailureResponse_keepStatus() {
        // given
        UUID orderId = UUID.randomUUID();

        Order order = createProcessingOrder();

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        mockAccountResponses(order);

        KisOrderInquiryResponse response =
                new KisOrderInquiryResponse(
                        "1",
                        "KIS_ERROR",
                        "주문 조회에 실패했습니다.",
                        "",
                        "",
                        List.of()
                );

        when(kisOrderClient.inquireDailyOrders(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(response);

        // when
        kisOrderReconciliationService.reconcile(orderId);

        // then
        verifyNoInteractions(kisOrderMatcher);

        verify(orderStatusCommandService, never())
                .recordReconciliationFailure(any());

        verify(orderStatusCommandService, never())
                .accept(any(), any());

        verify(orderStatusCommandService, never())
                .fail(any(), any(), any());
    }

    @Test
    @DisplayName("KIS 조회 결과가 미체결 취소 주문이면 CANCELED로 보정한다")
    void reconcileMatchedOrder_canceledWithoutFill_reconcileCanceled() {
        // given
        UUID orderId = UUID.randomUUID();

        KisOrderInquiryItem item =
                new KisOrderInquiryItem(
                        "20260906",
                        "00000",
                        "0001234567",
                        "02",
                        "005930",
                        "10",
                        "69900",
                        "100000",
                        "0",  // totalFilledQuantity
                        "0",  // averageExecutionPrice
                        "0",  // totalExecutionAmount
                        "0",  // remainingQuantity
                        "0",  // rejectedQuantity
                        "Y"
                );

        // when
        kisOrderReconciliationService.reconcileMatchedOrder(
                orderId,
                item
        );

        // then
        verify(orderExecutionCommandService)
                .applyReconciledCancellation(
                        eq(orderId),
                        eq("0001234567"),
                        eq(0),
                        any(BigDecimal.class),
                        eq(0L)
                );

        verify(orderStatusCommandService, never())
                .recordReconciliationFailure(any());

        verify(orderStatusCommandService, never())
                .accept(any(), any());

        verify(orderStatusCommandService, never())
                .fail(any(), any(), any());
    }

    @Test
    @DisplayName("계좌 정보 조회 오류는 주문 보정 실패 횟수에 포함하지 않는다")
    void reconcile_accountError_keepStatus() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createProcessingOrder();

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        when(accountClient.getAccessToken(order.getUserId()))
                .thenThrow(new RuntimeException("Account 조회 오류"));

        // when
        kisOrderReconciliationService.reconcile(orderId);

        // then
        verify(orderStatusCommandService, never())
                .recordReconciliationFailure(any());

        verifyNoInteractions(kisOrderClient);
        verifyNoInteractions(kisOrderMatcher);
    }

    @Test
    @DisplayName("매칭된 KIS 주문번호가 다른 Order에서 이미 사용 중이면 ACCEPTED로 보정하지 않는다")
    void reconcile_brokerOrderNoAlreadyUsed_recordFailure() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createProcessingOrder();

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        mockAccountResponses(order);

        KisOrderInquiryItem item =
                createItem(
                        "0001234567",
                        "7",
                        "0"
                );

        KisOrderInquiryResponse response =
                new KisOrderInquiryResponse(
                        "0",
                        "MCA00000",
                        "정상 처리되었습니다.",
                        "",
                        "",
                        List.of(item)
                );

        when(kisOrderClient.inquireDailyOrders(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString()
        )).thenReturn(response);

        when(kisOrderMatcher.match(order, response.output1()))
                .thenReturn(MatchResult.matched(item));

        when(orderQueryRepository
                .existsByBrokerOrderNoAndIdNotAndDeletedAtIsNull(
                        "0001234567",
                        orderId
                ))
                .thenReturn(true);

        // when
        kisOrderReconciliationService.reconcile(orderId);

        // then
        verify(orderStatusCommandService)
                .recordReconciliationFailure(orderId);

        verify(orderStatusCommandService, never())
                .accept(any(), any());
    }

    @Test
    @DisplayName("TIMEOUT 주문은 신규 주문을 재전송하지 않고 KIS 조회 결과로 ACCEPTED 보정한다")
    void reconcile_timeoutMatched_acceptWithoutReorder() {
        // given
        UUID orderId = UUID.randomUUID();

        Order order = createProcessingOrder();

        ReflectionTestUtils.setField(
                order,
                "status",
                OrderStatus.TIMEOUT
        );

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        mockAccountResponses(order);

        KisOrderInquiryItem item =
                createItem(
                        "0001234567",
                        "7",
                        "0"
                );

        KisOrderInquiryResponse response =
                new KisOrderInquiryResponse(
                        "0",
                        "MCA00000",
                        "정상 처리되었습니다.",
                        "",
                        "",
                        List.of(item)
                );

        when(kisOrderClient.inquireDailyOrders(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(response);

        when(kisOrderMatcher.match(order, response.output1()))
                .thenReturn(MatchResult.matched(item));

        // when
        kisOrderReconciliationService.reconcile(orderId);

        // then
        verify(kisOrderClient)
                .inquireDailyOrders(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString()
                );

        verify(kisOrderClient, never())
                .placeOrder(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                );

        verify(orderStatusCommandService)
                .accept(
                        orderId,
                        "0001234567"
                );
    }

    private Order createOrder() {
        return Order.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "005930",
                OrderType.BUY,
                69900L,
                10
        );
    }

    private KisOrderInquiryItem createItem(
            String orderNo,
            String orderQuantity,
            String rejectedQuantity
    ) {
        return new KisOrderInquiryItem(
                "20260906",
                "00000",
                orderNo,
                "02",
                "005930",
                orderQuantity,
                "69900",
                "100000",
                "0",
                "0",   // averageExecutionPrice
                "0",   // totalExecutionAmount
                orderQuantity,
                rejectedQuantity,
                "N"
        );
    }

    private void mockAccountResponses(Order order) {

        AccountTokenResponse tokenResponse =
                mock(AccountTokenResponse.class);

        when(tokenResponse.accessToken())
                .thenReturn("access-token");

        when(tokenResponse.appKey())
                .thenReturn("app-key");

        when(tokenResponse.secretKey())
                .thenReturn("secret-key");

        AccountOrderInfoResponse infoResponse =
                mock(AccountOrderInfoResponse.class);

        when(infoResponse.cano())
                .thenReturn("12345678");

        when(infoResponse.accountProductCode())
                .thenReturn("01");

        when(accountClient.getAccessToken(order.getUserId()))
                .thenReturn(tokenResponse);

        when(accountClient.getOrderInfo(order.getUserId()))
                .thenReturn(infoResponse);
    }

    private Order createProcessingOrder() {
        Order order = Order.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "005930",
                OrderType.BUY,
                69900L,
                7
        );

        order.startProcessing();

        ReflectionTestUtils.setField(
                order,
                "createdAt",
                Instant.parse("2026-09-06T01:00:00Z")
        );

        return order;
    }

    @Test
    @DisplayName("KIS 조회 결과가 미체결 취소 주문이면 보정 취소 처리한다")
    void reconcileMatchedOrder_canceledWithoutFill_applyReconciledCancellation() {
        // given
        UUID orderId = UUID.randomUUID();

        KisOrderInquiryItem item =
                new KisOrderInquiryItem(
                        "20260906",
                        "00000",
                        "0001234567",
                        "02",
                        "005930",
                        "10",
                        "69900",
                        "100000",
                        "0",
                        "0",
                        "0",
                        "0",
                        "0",
                        "Y"
                );

        // when
        kisOrderReconciliationService.reconcileMatchedOrder(
                orderId,
                item
        );

        // then
        verify(orderExecutionCommandService)
                .applyReconciledCancellation(
                        eq(orderId),
                        eq("0001234567"),
                        eq(0),
                        eq(BigDecimal.ZERO),
                        eq(0L)
                );

        verify(orderStatusCommandService, never())
                .recordReconciliationFailure(any());

        verify(orderStatusCommandService, never())
                .accept(any(), any());

        verify(orderStatusCommandService, never())
                .fail(any(), any(), any());
    }

    @Test
    @DisplayName("KIS 조회 결과가 일부 체결 후 취소 주문이면 체결 정보와 함께 보정 취소 처리한다")
    void reconcileMatchedOrder_partiallyFilledCanceled_applyReconciledCancellation() {
        // given
        UUID orderId = UUID.randomUUID();

        KisOrderInquiryItem item =
                new KisOrderInquiryItem(
                        "20260906",
                        "00000",
                        "0001234567",
                        "02",
                        "005930",
                        "10",
                        "69900",
                        "100000",
                        "3",      // totalFilledQuantity
                        "70000",  // averageExecutionPrice
                        "210000", // totalExecutionAmount
                        "0",
                        "0",
                        "Y"
                );

        // when
        kisOrderReconciliationService.reconcileMatchedOrder(
                orderId,
                item
        );

        // then
        verify(orderExecutionCommandService)
                .applyReconciledCancellation(
                        eq(orderId),
                        eq("0001234567"),
                        eq(3),
                        eq(new BigDecimal("70000")),
                        eq(210000L)
                );

        verify(orderStatusCommandService, never())
                .recordReconciliationFailure(any());

        verify(orderStatusCommandService, never())
                .accept(any(), any());

        verify(orderStatusCommandService, never())
                .fail(any(), any(), any());
    }

    @Test
    @DisplayName("취소되지 않은 주문은 체결 정보 필드가 비어 있어도 ACCEPTED로 보정한다")
    void reconcileMatchedOrder_notCanceled_acceptWithoutExecutionFields() {
        // given
        UUID orderId = UUID.randomUUID();

        KisOrderInquiryItem item =
                new KisOrderInquiryItem(
                        "20260906",
                        "00000",
                        "0001234567",
                        "02",
                        "005930",
                        "10",
                        "69900",
                        "100000",
                        "0",
                        "",   // averageExecutionPrice
                        "",   // totalExecutionAmount
                        "10",
                        "0",
                        "N"
                );

        // when
        kisOrderReconciliationService.reconcileMatchedOrder(
                orderId,
                item
        );

        // then
        verify(orderStatusCommandService)
                .accept(
                        orderId,
                        "0001234567"
                );

        verify(orderStatusCommandService, never())
                .recordReconciliationFailure(any());

        verify(orderExecutionCommandService, never())
                .applyReconciledCancellation(
                        any(),
                        any(),
                        anyInt(),
                        any(),
                        anyLong()
                );
    }

    @Test
    @DisplayName("취소 주문 보정 중 주문 정보 불일치가 발생하면 재조정 실패를 기록한다")
    void reconcileMatchedOrder_canceledInvalidOrder_recordFailure() {
        // given
        UUID orderId = UUID.randomUUID();

        KisOrderInquiryItem item =
                new KisOrderInquiryItem(
                        "20260906",
                        "00000",
                        "0001234567",
                        "02",
                        "005930",
                        "10",
                        "69900",
                        "100000",
                        "3",      // totalFilledQuantity
                        "70000",  // averageExecutionPrice
                        "210000", // totalExecutionAmount
                        "0",
                        "0",
                        "Y"
                );

        doThrow(
                new BusinessException(
                        TradingErrorCode.INVALID_ORDER
                )
        )
                .when(orderExecutionCommandService)
                .applyReconciledCancellation(
                        eq(orderId),
                        eq("0001234567"),
                        eq(3),
                        eq(new BigDecimal("70000")),
                        eq(210000L)
                );

        // when
        kisOrderReconciliationService.reconcileMatchedOrder(
                orderId,
                item
        );

        // then
        verify(orderStatusCommandService)
                .recordReconciliationFailure(orderId);
    }

    @Test
    @DisplayName("취소 주문 보정 전에 상태가 변경되면 재조정 실패로 기록하지 않는다")
    void reconcileMatchedOrder_statusAlreadyChanged_doNotRecordFailure() {
        // given
        UUID orderId = UUID.randomUUID();

        KisOrderInquiryItem item =
                new KisOrderInquiryItem(
                        "20260906",
                        "00000",
                        "0001234567",
                        "02",
                        "005930",
                        "10",
                        "69900",
                        "100000",
                        "3",
                        "70000",
                        "210000",
                        "0",
                        "0",
                        "Y"
                );

        doThrow(
                new BusinessException(
                        TradingErrorCode.ORDER_STATUS_CHANGE_NOT_ALLOWED
                )
        )
                .when(orderExecutionCommandService)
                .applyReconciledCancellation(
                        eq(orderId),
                        eq("0001234567"),
                        eq(3),
                        eq(new BigDecimal("70000")),
                        eq(210000L)
                );

        // when
        kisOrderReconciliationService.reconcileMatchedOrder(
                orderId,
                item
        );

        // then
        verify(orderStatusCommandService, never())
                .recordReconciliationFailure(any());
    }
}