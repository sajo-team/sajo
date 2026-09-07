package com.sajo.trading_service.trading.controller;
 
import com.sajo.common.config.CommonPageableAutoConfiguration;
import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.trading_service.trading.controller.dto.response.OrderDetailResponse;
import com.sajo.trading_service.trading.controller.dto.response.OrderListResponse;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.service.query.OrderQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
 
import java.time.Instant;
import java.util.List;
import java.util.UUID;
 
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
 
@WebMvcTest(OrderController.class)
@Import({
        GlobalExceptionHandler.class,
        CommonPageableAutoConfiguration.class
})
class OrderControllerTest {
 
    @Autowired
    private MockMvc mockMvc;
 
    @MockitoBean
    private OrderQueryService orderQueryService;
 
    @Test
    @DisplayName("주문 목록 조회에 성공한다")
    void getAllOrders_success() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
 
        OrderListResponse response =
                new OrderListResponse(
                        orderId,
                        autoTradingId,
                        strategyId,
                        "005930",
                        OrderType.BUY,
                        69900L,
                        7,
                        489300L,
                        OrderStatus.REQUESTED,
                        Instant.now()
                );
 
        when(orderQueryService.findOrdersByUserId(
                eq(userId),
                any()
        )).thenReturn(
                new PageImpl<>(List.of(response))
        );
 
        // when & then
        mockMvc.perform(
                        get("/api/v1/orders")
                                .header("X-User-Id", userId.toString())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].orderId")
                        .value(orderId.toString()))
                .andExpect(jsonPath("$.data.content[0].autoTradingId")
                        .value(autoTradingId.toString()))
                .andExpect(jsonPath("$.data.content[0].strategyId")
                        .value(strategyId.toString()))
                .andExpect(jsonPath("$.data.content[0].stockCode")
                        .value("005930"))
                .andExpect(jsonPath("$.data.content[0].orderType")
                        .value("BUY"))
                .andExpect(jsonPath("$.data.content[0].status")
                        .value("REQUESTED"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }
 
    @Test
    @DisplayName("주문 목록이 없으면 빈 페이지를 반환한다")
    void getAllOrders_empty() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
 
        PageRequest pageable = PageRequest.of(0, 10);
 
        when(orderQueryService.findOrdersByUserId(
                eq(userId),
                any()
        )).thenReturn(
                Page.empty(pageable)
        );
 
        // when & then
        mockMvc.perform(
                        get("/api/v1/orders")
                                .header("X-User-Id", userId.toString())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0));
    }
 
    @Test
    @DisplayName("주문 상세 조회에 성공한다")
    void getOrderDetail_success() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID signalId = UUID.randomUUID();
 
        Instant now = Instant.now();
 
        OrderDetailResponse response =
                new OrderDetailResponse(
                        orderId,
                        autoTradingId,
                        strategyId,
                        signalId,
                        "005930",
                        OrderType.BUY,
                        69900L,
                        7,
                        489300L,
                        OrderStatus.ACCEPTED,
                        "0001234567",
                        null,
                        null,
                        now,
                        now
                );
 
        when(orderQueryService.findOrderByIdAndUserId(
                orderId,
                userId
        )).thenReturn(response);
 
        // when & then
        mockMvc.perform(
                        get("/api/v1/orders/{orderId}", orderId)
                                .header("X-User-Id", userId.toString())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId")
                        .value(orderId.toString()))
                .andExpect(jsonPath("$.data.signalId")
                        .value(signalId.toString()))
                .andExpect(jsonPath("$.data.stockCode")
                        .value("005930"))
                .andExpect(jsonPath("$.data.orderType")
                        .value("BUY"))
                .andExpect(jsonPath("$.data.status")
                        .value("ACCEPTED"))
                .andExpect(jsonPath("$.data.brokerOrderNo")
                        .value("0001234567"));
    }
 
    @Test
    @DisplayName("주문을 찾을 수 없으면 404를 반환한다")
    void getOrderDetail_notFound() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
 
        when(orderQueryService.findOrderByIdAndUserId(
                orderId,
                userId
        )).thenThrow(
                new BusinessException(
                        TradingErrorCode.ORDER_NOT_FOUND
                )
        );
 
        // when & then
        mockMvc.perform(
                        get("/api/v1/orders/{orderId}", orderId)
                                .header("X-User-Id", userId.toString())
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode")
                        .value("AUTO_TRADING_0011"));
    }
 
    @Test
    @DisplayName("주문 목록 조회 시 정렬 조건이 없으면 생성일 기준 최신순으로 조회한다")
    void getAllOrders_defaultSortCreatedAtDesc() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
 
        when(orderQueryService.findOrdersByUserId(
                eq(userId),
                any(Pageable.class)
        )).thenReturn(Page.empty(PageRequest.of(0, 10)));
 
        // when
        mockMvc.perform(
                        get("/api/v1/orders")
                                .header("X-User-Id", userId.toString())
                )
                .andExpect(status().isOk());
 
        // then
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);
 
        verify(orderQueryService).findOrdersByUserId(
                eq(userId),
                pageableCaptor.capture()
        );
 
        Pageable pageable = pageableCaptor.getValue();
 
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(10);
 
        Sort.Order createdAtSort =
                pageable.getSort().getOrderFor("createdAt");
 
        assertThat(createdAtSort).isNotNull();
        assertThat(createdAtSort.getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }
 
    @Test
    @DisplayName("X-User-Id 헤더 없이 요청하면 400을 반환한다 (Gateway를 거치지 않은 요청)")
    void getAllOrders_withoutUserIdHeader() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON_0001"));
    }
}
