package com.sajo.trading_service.trading.controller;

import com.sajo.trading_service.trading.controller.dto.request.ExecutionAdminSearchCondition;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.service.query.ExecutionQueryService;
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

@WebMvcTest(ExecutionAdminController.class)
class ExecutionAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExecutionQueryService executionQueryService;

    @Test
    @DisplayName("ADMIN 권한이면 관리자 체결 목록을 조회할 수 있다")
    void getAllExecutionsWithAdminRole() throws Exception {
        // given
        given(executionQueryService.findAllExecutionsForAdmin(
                any(ExecutionAdminSearchCondition.class),
                any(Pageable.class)
        )).willReturn(Page.empty());

        // when & then
        mockMvc.perform(
                        get("/api/v1/admin/trading/executions")
                                .header("X-User-Role", "ADMIN")
                )
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN 권한이 아니면 관리자 체결 조회가 거부된다")
    void getAllExecutionsWithoutAdminRole() {
        // when & then
        ServletException exception = assertThrows(
                ServletException.class,
                () -> mockMvc.perform(
                        get("/api/v1/admin/trading/executions")
                                .header("X-User-Role", "USER")
                )
        );

        assertThat(exception.getCause())
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(executionQueryService);
    }

    @Test
    @DisplayName("X-User-Role 헤더가 없으면 관리자 체결 조회가 실패한다")
    void getAllExecutionsWithoutRoleHeader() throws Exception {
        mockMvc.perform(
                        get("/api/v1/admin/trading/executions")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(executionQueryService);
    }

    @Test
    @DisplayName("관리자 체결 조회 조건이 서비스에 전달된다")
    void getAllExecutionsWithCondition() throws Exception {
        // given
        given(executionQueryService.findAllExecutionsForAdmin(
                any(ExecutionAdminSearchCondition.class),
                any(Pageable.class)
        )).willReturn(Page.empty());

        // when
        mockMvc.perform(
                        get("/api/v1/admin/trading/executions")
                                .header("X-User-Role", "ADMIN")
                                .param("stockCode", "005930")
                                .param("orderType", "SELL")
                )
                .andExpect(status().isOk());

        // then
        ArgumentCaptor<ExecutionAdminSearchCondition> captor =
                ArgumentCaptor.forClass(
                        ExecutionAdminSearchCondition.class
                );

        verify(executionQueryService)
                .findAllExecutionsForAdmin(
                        captor.capture(),
                        any(Pageable.class)
                );

        ExecutionAdminSearchCondition condition =
                captor.getValue();

        assertThat(condition.stockCode())
                .isEqualTo("005930");

        assertThat(condition.orderType())
                .isEqualTo(OrderType.SELL);
    }
}