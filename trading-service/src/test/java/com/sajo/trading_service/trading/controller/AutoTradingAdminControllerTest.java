package com.sajo.trading_service.trading.controller;

import com.sajo.trading_service.trading.controller.dto.request.AutoTradingAdminSearchCondition;
import com.sajo.trading_service.trading.domain.enums.AutoTradingDirection;
import com.sajo.trading_service.trading.service.query.AutoTradingQueryService;
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

@WebMvcTest(AutoTradingAdminController.class)
class AutoTradingAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AutoTradingQueryService autoTradingQueryService;

    @Test
    @DisplayName("ADMIN 권한이면 관리자 AutoTrading 목록을 조회할 수 있다")
    void getAllAutoTradingsWithAdminRole() throws Exception {
        // given
        given(autoTradingQueryService.findAllAutoTradingForAdmin(
                any(AutoTradingAdminSearchCondition.class),
                any(Pageable.class)
        )).willReturn(Page.empty());

        // when & then
        mockMvc.perform(
                        get("/api/v1/admin/trading/auto-tradings")
                                .header("X-User-Role", "ADMIN")
                )
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN 권한이 아니면 관리자 AutoTrading 조회가 거부된다")
    void getAllAutoTradingsWithoutAdminRole() {
        // when & then
        ServletException exception = assertThrows(
                ServletException.class,
                () -> mockMvc.perform(
                        get("/api/v1/admin/trading/auto-tradings")
                                .header("X-User-Role", "USER")
                )
        );

        assertThat(exception.getCause())
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(autoTradingQueryService);
    }

    @Test
    @DisplayName("X-User-Role 헤더가 없으면 관리자 AutoTrading 조회가 실패한다")
    void getAllAutoTradingsWithoutRoleHeader() throws Exception {
        mockMvc.perform(
                        get("/api/v1/admin/trading/auto-tradings")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(autoTradingQueryService);
    }

    @Test
    @DisplayName("관리자 AutoTrading 조회 조건이 서비스에 전달된다")
    void getAllAutoTradingsWithCondition() throws Exception {
        // given
        given(autoTradingQueryService.findAllAutoTradingForAdmin(
                any(AutoTradingAdminSearchCondition.class),
                any(Pageable.class)
        )).willReturn(Page.empty());

        // when
        mockMvc.perform(
                        get("/api/v1/admin/trading/auto-tradings")
                                .header("X-User-Role", "ADMIN")
                                .param("direction", "SELL_ONLY")
                                .param("enabled", "true")
                )
                .andExpect(status().isOk());

        // then
        ArgumentCaptor<AutoTradingAdminSearchCondition> captor =
                ArgumentCaptor.forClass(
                        AutoTradingAdminSearchCondition.class
                );

        verify(autoTradingQueryService)
                .findAllAutoTradingForAdmin(
                        captor.capture(),
                        any(Pageable.class)
                );

        AutoTradingAdminSearchCondition condition =
                captor.getValue();

        assertThat(condition.direction())
                .isEqualTo(AutoTradingDirection.SELL_ONLY);

        assertThat(condition.enabled())
                .isTrue();
    }
}