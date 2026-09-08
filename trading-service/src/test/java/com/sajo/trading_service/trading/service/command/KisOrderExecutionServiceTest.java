package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.client.AccountClient;
import com.sajo.trading_service.trading.client.KisOrderClient;
import com.sajo.trading_service.trading.client.dto.response.AccountOrderInfoResponse;
import com.sajo.trading_service.trading.client.dto.response.AccountTokenResponse;
import com.sajo.trading_service.trading.client.dto.response.KisOrderInquiryItem;
import com.sajo.trading_service.trading.client.dto.response.KisOrderInquiryResponse;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.AccountType;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.query.OrderQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KisOrderExecutionServiceTest {

    @Mock
    private OrderQueryRepository orderQueryRepository;

    @Mock
    private AccountClient accountClient;

    @Mock
    private KisOrderClient kisOrderClient;

    @Mock
    private OrderExecutionCommandService orderExecutionCommandService;

    private KisOrderExecutionService kisOrderExecutionService;

    @BeforeEach
    void setUp() {
        kisOrderExecutionService =
                new KisOrderExecutionService(
                        orderQueryRepository,
                        accountClient,
                        kisOrderClient,
                        orderExecutionCommandService
                );
    }

    @Test
    @DisplayName("부분 체결 조회 결과를 OrderExecutionCommandService에 전달한다")
    void processExecution_partialFill() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        mockAccountResponses(order);

        KisOrderInquiryItem item =
                createInquiryItem(
                        order.getBrokerOrderNo(),
                        "2",
                        "2",
                        "69800",
                        "139600",
                        "N"
                );

        when(kisOrderClient.inquireDailyOrders(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(
                successResponse(List.of(item))
        );

        // when
        kisOrderExecutionService.processExecution(orderId);

        // then
        verify(orderExecutionCommandService)
                .applyExecution(
                        orderId,
                        2,
                        2,
                        new BigDecimal("69800"),
                        139_600L
                );

        verify(orderExecutionCommandService, never())
                .applyCancellation(
                        any(),
                        anyInt(),
                        anyInt(),
                        any(BigDecimal.class),
                        anyLong()
                );
    }

    @Test
    @DisplayName("전체 체결 조회 결과를 OrderExecutionCommandService에 전달한다")
    void processExecution_fullFill() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        mockAccountResponses(order);

        KisOrderInquiryItem item =
                createInquiryItem(
                        order.getBrokerOrderNo(),
                        "4",
                        "0",
                        "70000",
                        "280000",
                        "N"
                );

        when(kisOrderClient.inquireDailyOrders(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any()
        )).thenReturn(successResponse(List.of(item)));

        // when
        kisOrderExecutionService.processExecution(orderId);

        // then
        verify(orderExecutionCommandService)
                .applyExecution(
                        orderId,
                        4,
                        0,
                        new BigDecimal("70000"),
                        280_000L
                );
    }

    @Test
    @DisplayName("체결 없이 전체 취소된 주문도 취소 처리한다")
    void processExecution_cancelWithoutFill() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        mockAccountResponses(order);

        KisOrderInquiryItem item =
                createInquiryItem(
                        order.getBrokerOrderNo(),
                        "0",
                        "0",
                        "0",
                        "0",
                        "Y"
                );

        when(kisOrderClient.inquireDailyOrders(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any()
        )).thenReturn(successResponse(List.of(item)));

        // when
        kisOrderExecutionService.processExecution(orderId);

        // then
        verify(orderExecutionCommandService)
                .applyCancellation(
                        orderId,
                        0,
                        0,
                        new BigDecimal("0"),
                        0L
                );

        verify(orderExecutionCommandService, never())
                .applyExecution(
                        any(),
                        anyInt(),
                        anyInt(),
                        any(BigDecimal.class),
                        anyLong()
                );
    }

    @Test
    @DisplayName("부분 체결 후 취소 결과를 취소 처리한다")
    void processExecution_cancelAfterPartialFill() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        mockAccountResponses(order);

        KisOrderInquiryItem item =
                createInquiryItem(
                        order.getBrokerOrderNo(),
                        "2",
                        "0",
                        "69800",
                        "139600",
                        "Y"
                );

        when(kisOrderClient.inquireDailyOrders(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any()
        )).thenReturn(successResponse(List.of(item)));

        // when
        kisOrderExecutionService.processExecution(orderId);

        // then
        verify(orderExecutionCommandService)
                .applyCancellation(
                        orderId,
                        2,
                        0,
                        new BigDecimal("69800"),
                        139_600L
                );
    }

    @Test
    @DisplayName("체결 수량이 0이고 취소 주문이 아니면 상태를 변경하지 않는다")
    void processExecution_noFill() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        mockAccountResponses(order);

        KisOrderInquiryItem item =
                createInquiryItem(
                        order.getBrokerOrderNo(),
                        "0",
                        "4",
                        "0",
                        "0",
                        "N"
                );

        when(kisOrderClient.inquireDailyOrders(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any()
        )).thenReturn(successResponse(List.of(item)));

        // when
        kisOrderExecutionService.processExecution(orderId);

        // then
        verifyNoInteractions(orderExecutionCommandService);
    }

    @Test
    @DisplayName("계좌 정보 조회 실패 시 주문 체결 상태를 변경하지 않는다")
    void processExecution_accountFailure() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        when(accountClient.getAccessToken(order.getUserId()))
                .thenThrow(new RuntimeException("Account Service Error"));

        // when
        kisOrderExecutionService.processExecution(orderId);

        // then
        verifyNoInteractions(kisOrderClient);
        verifyNoInteractions(orderExecutionCommandService);
    }

    @Test
    @DisplayName("KIS 체결 조회 예외 발생 시 주문 체결 상태를 변경하지 않는다")
    void processExecution_kisFailure() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        mockAccountResponses(order);

        when(kisOrderClient.inquireDailyOrders(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any()
        )).thenThrow(new RuntimeException("KIS Error"));

        // when
        kisOrderExecutionService.processExecution(orderId);

        // then
        verifyNoInteractions(orderExecutionCommandService);
    }

    @Test
    @DisplayName("KIS 응답이 실패이면 주문 체결 상태를 변경하지 않는다")
    void processExecution_kisFailureResponse() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        mockAccountResponses(order);

        KisOrderInquiryResponse response =
                new KisOrderInquiryResponse(
                        "1",
                        "KIS_ERROR",
                        "조회 실패",
                        "",
                        "",
                        List.of()
                );

        when(kisOrderClient.inquireDailyOrders(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any()
        )).thenReturn(response);

        // when
        kisOrderExecutionService.processExecution(orderId);

        // then
        verifyNoInteractions(orderExecutionCommandService);
    }

    @Test
    @DisplayName("KIS 주문번호와 일치하는 체결 결과가 없으면 반영하지 않는다")
    void processExecution_notMatched() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        mockAccountResponses(order);

        KisOrderInquiryItem item =
                createInquiryItem(
                        "OTHER_ORDER_NO",
                        "2",
                        "2",
                        "69800",
                        "139600",
                        "N"
                );

        when(kisOrderClient.inquireDailyOrders(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any()
        )).thenReturn(successResponse(List.of(item)));

        // when
        kisOrderExecutionService.processExecution(orderId);

        // then
        verifyNoInteractions(orderExecutionCommandService);
    }

    @Test
    @DisplayName("체결 수량 파싱에 실패하면 체결 결과를 반영하지 않는다")
    void processExecution_invalidQuantity() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        mockAccountResponses(order);

        KisOrderInquiryItem item =
                createInquiryItem(
                        order.getBrokerOrderNo(),
                        "2.5",
                        "1",
                        "69800",
                        "139600",
                        "N"
                );

        when(kisOrderClient.inquireDailyOrders(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any()
        )).thenReturn(successResponse(List.of(item)));

        // when
        kisOrderExecutionService.processExecution(orderId);

        // then
        verifyNoInteractions(orderExecutionCommandService);
    }

    private void mockAccountResponses(Order order) {
        when(accountClient.getAccessToken(order.getUserId()))
                .thenReturn(
                        new AccountTokenResponse(
                                "access-token",
                                "app-key",
                                "secret-key"
                        )
                );

        when(accountClient.getOrderInfo(order.getUserId()))
                .thenReturn(
                        new AccountOrderInfoResponse(
                                "12345678",
                                "01",
                                AccountType.VIRTUAL
                        )
                );
    }

    private KisOrderInquiryResponse successResponse(
            List<KisOrderInquiryItem> items
    ) {
        return new KisOrderInquiryResponse(
                "0",
                "SUCCESS",
                "정상 처리",
                "",
                "",
                items
        );
    }

    private KisOrderInquiryItem createInquiryItem(
            String orderNo,
            String totalFilledQuantity,
            String remainingQuantity,
            String averageExecutionPrice,
            String totalExecutionAmount,
            String canceled
    ) {
        return new KisOrderInquiryItem(
                "20260908",
                "00000",
                orderNo,
                "02",
                "005930",
                "4",
                "70000",
                "100000",
                totalFilledQuantity,
                averageExecutionPrice,
                totalExecutionAmount,
                remainingQuantity,
                "0",
                canceled
        );
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

        ReflectionTestUtils.setField(
                order,
                "createdAt",
                Instant.now()
        );

        order.startProcessing();
        order.accept("0001234567");

        return order;
    }

    @Test
    @DisplayName("취소 결과 반영 중 BusinessException이 발생해도 외부로 전파하지 않는다")
    void processExecution_cancellationBusinessException() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        mockAccountResponses(order);

        KisOrderInquiryItem item =
                createInquiryItem(
                        order.getBrokerOrderNo(),
                        "2",
                        "1",
                        "69800",
                        "139600",
                        "Y"
                );

        when(kisOrderClient.inquireDailyOrders(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any()
        )).thenReturn(
                successResponse(List.of(item))
        );

        doThrow(
                new BusinessException(
                        TradingErrorCode.INVALID_ORDER
                )
        ).when(orderExecutionCommandService)
                .applyCancellation(
                        orderId,
                        2,
                        1,
                        new BigDecimal("69800"),
                        139_600L
                );

// when
        kisOrderExecutionService.processExecution(orderId);

// then
        verify(orderExecutionCommandService)
                .applyCancellation(
                        orderId,
                        2,
                        1,
                        new BigDecimal("69800"),
                        139_600L
                );
    }

    @Test
    @DisplayName("평균 체결가가 소수여도 정상적으로 체결 결과를 반영한다")
    void processExecution_decimalAveragePrice() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        mockAccountResponses(order);

        KisOrderInquiryItem item =
                createInquiryItem(
                        order.getBrokerOrderNo(),
                        "3",
                        "1",
                        "69816.6667",
                        "209450",
                        "N"
                );

        when(kisOrderClient.inquireDailyOrders(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any()
        )).thenReturn(
                successResponse(List.of(item))
        );

        // when
        kisOrderExecutionService.processExecution(orderId);

        // then
        verify(orderExecutionCommandService)
                .applyExecution(
                        orderId,
                        3,
                        1,
                        new BigDecimal("69816.6667"),
                        209_450L
                );
    }

    @Test
    @DisplayName("거절 수량이 존재하면 체결 결과를 자동 반영하지 않는다")
    void processExecution_rejectedQuantity() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = createAcceptedOrder();

        when(orderQueryRepository.findByIdAndDeletedAtIsNull(orderId))
                .thenReturn(Optional.of(order));

        mockAccountResponses(order);

        KisOrderInquiryItem item =
                new KisOrderInquiryItem(
                        "20260908",
                        "00000",
                        order.getBrokerOrderNo(),
                        "02",
                        "005930",
                        "4",
                        "70000",
                        "100000",
                        "2",
                        "69800",
                        "139600",
                        "0",
                        "2",
                        "N"
                );

        when(kisOrderClient.inquireDailyOrders(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any()
        )).thenReturn(
                successResponse(List.of(item))
        );

        // when
        kisOrderExecutionService.processExecution(orderId);

        // then
        verifyNoInteractions(orderExecutionCommandService);
    }
}
