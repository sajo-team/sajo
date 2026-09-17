package com.sajo.trading_service.trading.controller;

import com.sajo.trading_service.trading.controller.dto.request.OrderAdminSearchCondition;
import com.sajo.trading_service.trading.controller.dto.request.OrderManualResolutionRequest;
import com.sajo.trading_service.trading.domain.enums.OrderManualResolution;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;
import com.sajo.trading_service.trading.service.command.OrderAdminCommandService;
import com.sajo.trading_service.trading.service.query.OrderQueryService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderAdminController.class)
class OrderAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderQueryService orderQueryService;

    @MockitoBean
    private OrderAdminCommandService orderAdminCommandService;

    @Test
    @DisplayName("ADMIN 권한이면 관리자 주문 목록을 조회할 수 있다")
    void getAllOrdersWithAdminRole() throws Exception {
        // given
        given(orderQueryService.findAllOrdersForAdmin(
                any(OrderAdminSearchCondition.class),
                any(Pageable.class)
        )).willReturn(Page.empty());

        // when & then
        mockMvc.perform(
                        get("/api/v1/admin/trading/orders")
                                .header("X-User-Role", "ADMIN")
                )
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN 권한이 아니면 관리자 주문 조회가 거부된다")
    void getAllOrdersWithoutAdminRole() {
        // when & then
        ServletException exception = assertThrows(
                ServletException.class,
                () -> mockMvc.perform(
                        get("/api/v1/admin/trading/orders")
                                .header("X-User-Role", "USER")
                )
        );

        assertThat(exception.getCause())
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(orderQueryService);
    }

    @Test
    @DisplayName("X-User-Role 헤더가 없으면 관리자 주문 조회가 실패한다")
    void getAllOrdersWithoutRoleHeader() throws Exception {
        mockMvc.perform(
                        get("/api/v1/admin/trading/orders")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderQueryService);
    }

    @Test
    @DisplayName("관리자 주문 조회 조건이 서비스에 전달된다")
    void getAllOrdersWithCondition() throws Exception {
        // given
        given(orderQueryService.findAllOrdersForAdmin(
                any(OrderAdminSearchCondition.class),
                any(Pageable.class)
        )).willReturn(Page.empty());

        // when
        mockMvc.perform(
                        get("/api/v1/admin/trading/orders")
                                .header("X-User-Role", "ADMIN")
                                .param("stockCode", "005930")
                                .param("status", "TIMEOUT")
                                .param(
                                        "failureCode",
                                        "KIS_RECONCILIATION_EXHAUSTED"
                                )
                )
                .andExpect(status().isOk());

        // then
        ArgumentCaptor<OrderAdminSearchCondition> captor =
                ArgumentCaptor.forClass(
                        OrderAdminSearchCondition.class
                );

        verify(orderQueryService)
                .findAllOrdersForAdmin(
                        captor.capture(),
                        any(Pageable.class)
                );

        OrderAdminSearchCondition condition =
                captor.getValue();

        assertThat(condition.stockCode())
                .isEqualTo("005930");

        assertThat(condition.status())
                .isEqualTo(OrderStatus.TIMEOUT);

        assertThat(condition.failureCode())
                .isEqualTo("KIS_RECONCILIATION_EXHAUSTED");
    }

    @Test
    @DisplayName("ADMIN 권한이면 TIMEOUT 주문을 수동 확정할 수 있다")
    void resolveTimeoutOrderWithAdminRole() throws Exception {
        // given
        UUID orderId = UUID.randomUUID();

        // when & then
        mockMvc.perform(
                        patch(
                                "/api/v1/admin/trading/orders/{orderId}/resolutions",
                                orderId
                        )
                                .header("X-User-Role", "ADMIN")
                                .contentType("application/json")
                                .content("""
                                    {
                                      "resolution": "FAILED",
                                      "brokerOrderNo": null,
                                      "totalFilledQuantity": null,
                                      "averageExecutionPrice": null,
                                      "totalExecutionAmount": null,
                                      "reason": "KIS 확인 결과 주문 접수 내역 없음"
                                    }
                                    """)
                )
                .andExpect(status().isOk());

        verify(orderAdminCommandService)
                .resolveTimeoutOrder(
                        eq(orderId),
                        any(OrderManualResolutionRequest.class)
                );
    }

    @Test
    @DisplayName("ADMIN 권한이 아니면 TIMEOUT 주문 수동 확정이 거부된다")
    void resolveTimeoutOrderWithoutAdminRole() {
        // given
        UUID orderId = UUID.randomUUID();

        // when & then
        ServletException exception = assertThrows(
                ServletException.class,
                () -> mockMvc.perform(
                        patch(
                                "/api/v1/admin/trading/orders/{orderId}/resolutions",
                                orderId
                        )
                                .header("X-User-Role", "USER")
                                .contentType("application/json")
                                .content("""
                                    {
                                      "resolution": "FAILED",
                                      "reason": "관리자 확인"
                                    }
                                    """)
                )
        );

        assertThat(exception.getCause())
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(orderAdminCommandService);
    }

    @Test
    @DisplayName("X-User-Role 헤더가 없으면 TIMEOUT 주문 수동 확정이 실패한다")
    void resolveTimeoutOrderWithoutRoleHeader() throws Exception {
        // given
        UUID orderId = UUID.randomUUID();

        // when & then
        mockMvc.perform(
                        patch(
                                "/api/v1/admin/trading/orders/{orderId}/resolutions",
                                orderId
                        )
                                .contentType("application/json")
                                .content("""
                                    {
                                      "resolution": "FAILED",
                                      "reason": "관리자 확인"
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderAdminCommandService);
    }

    @Test
    @DisplayName("수동 확정 요청값이 서비스에 전달된다")
    void resolveTimeoutOrderRequestBinding() throws Exception {
        // given
        UUID orderId = UUID.randomUUID();

        ArgumentCaptor<OrderManualResolutionRequest> captor =
                ArgumentCaptor.forClass(
                        OrderManualResolutionRequest.class
                );

        // when
        mockMvc.perform(
                        patch(
                                "/api/v1/admin/trading/orders/{orderId}/resolutions",
                                orderId
                        )
                                .header("X-User-Role", "ADMIN")
                                .contentType("application/json")
                                .content("""
                                    {
                                      "resolution": "CANCELED",
                                      "brokerOrderNo": "0001234567",
                                      "totalFilledQuantity": 3,
                                      "averageExecutionPrice": 70000,
                                      "totalExecutionAmount": 210000,
                                      "reason": "3주 체결 후 잔량 취소 확인"
                                    }
                                    """)
                )
                .andExpect(status().isOk());

        // then
        verify(orderAdminCommandService)
                .resolveTimeoutOrder(
                        eq(orderId),
                        captor.capture()
                );

        OrderManualResolutionRequest request =
                captor.getValue();

        assertThat(request.resolution())
                .isEqualTo(OrderManualResolution.CANCELED);

        assertThat(request.brokerOrderNo())
                .isEqualTo("0001234567");

        assertThat(request.totalFilledQuantity())
                .isEqualTo(3);

        assertThat(request.averageExecutionPrice())
                .isEqualByComparingTo("70000");

        assertThat(request.totalExecutionAmount())
                .isEqualTo(210_000L);

        assertThat(request.reason())
                .isEqualTo("3주 체결 후 잔량 취소 확인");
    }

    @Test
    @DisplayName("resolution이 없으면 수동 확정 요청이 실패한다")
    void resolveTimeoutOrderWithoutResolution() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(
                        patch(
                                "/api/v1/admin/trading/orders/{orderId}/resolutions",
                                orderId
                        )
                                .header("X-User-Role", "ADMIN")
                                .contentType("application/json")
                                .content("""
                                    {
                                      "reason": "관리자 확인"
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderAdminCommandService);
    }
}