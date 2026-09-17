package com.sajo.trading_service.trading.controller;

import com.sajo.trading_service.trading.controller.dto.request.OrderAdminSearchCondition;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderAdminController.class)
class OrderAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderQueryService orderQueryService;

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
}