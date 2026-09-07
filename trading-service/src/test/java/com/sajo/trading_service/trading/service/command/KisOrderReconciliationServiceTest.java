package com.sajo.trading_service.trading.service.command;

import com.sajo.trading_service.trading.client.AccountClient;
import com.sajo.trading_service.trading.client.KisOrderClient;
import com.sajo.trading_service.trading.client.dto.response.AccountOrderInfoResponse;
import com.sajo.trading_service.trading.client.dto.response.AccountTokenResponse;
import com.sajo.trading_service.trading.client.dto.response.KisOrderInquiryItem;
import com.sajo.trading_service.trading.client.dto.response.KisOrderInquiryResponse;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;
import com.sajo.trading_service.trading.domain.enums.OrderType;
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
        verifyNoInteractions(orderStatusCommandService);
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
        verifyNoInteractions(orderStatusCommandService);
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
        verifyNoInteractions(orderStatusCommandService);
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
        verifyNoInteractions(orderStatusCommandService);
    }

    @Test
    @DisplayName("KIS 주문 조회 중 오류가 발생하면 기존 상태를 유지한다")
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
        verifyNoInteractions(orderStatusCommandService);
        verifyNoInteractions(kisOrderMatcher);
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
    @DisplayName("KIS 주문 조회가 실패 응답이면 기존 상태를 유지한다")
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
        verifyNoInteractions(orderStatusCommandService);
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
}