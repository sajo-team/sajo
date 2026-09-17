package com.sajo.trading_service.trading.controller;

import com.sajo.trading_service.trading.controller.dto.request.AutoTradingAdminSearchCondition;
import com.sajo.trading_service.trading.domain.enums.AutoTradingDirection;
import com.sajo.trading_service.trading.service.command.AutoTradingAdminCommandService;
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

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AutoTradingAdminController.class)
class AutoTradingAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AutoTradingQueryService autoTradingQueryService;

    @MockitoBean
    private AutoTradingAdminCommandService autoTradingAdminCommandService;

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

    @Test
    @DisplayName("ADMIN 권한이면 AutoTrading을 긴급 중지할 수 있다")
    void suspendAutoTrading() throws Exception {
        UUID autoTradingId = UUID.randomUUID();

        mockMvc.perform(
                        patch(
                                "/api/v1/admin/trading/auto-tradings/{autoTradingId}/suspensions",
                                autoTradingId
                        )
                                .header("X-User-Role", "ADMIN")
                                .contentType("application/json")
                                .content("""
                                    {
                                      "suspended": true
                                    }
                                    """)
                )
                .andExpect(status().isOk());

        verify(autoTradingAdminCommandService)
                .suspend(autoTradingId);

        verify(autoTradingAdminCommandService, never())
                .resume(any());
    }

    @Test
    @DisplayName("ADMIN 권한이면 AutoTrading 긴급 중지를 해제할 수 있다")
    void resumeAutoTrading() throws Exception {
        UUID autoTradingId = UUID.randomUUID();

        mockMvc.perform(
                        patch(
                                "/api/v1/admin/trading/auto-tradings/{autoTradingId}/suspensions",
                                autoTradingId
                        )
                                .header("X-User-Role", "ADMIN")
                                .contentType("application/json")
                                .content("""
                                    {
                                      "suspended": false
                                    }
                                    """)
                )
                .andExpect(status().isOk());

        verify(autoTradingAdminCommandService)
                .resume(autoTradingId);

        verify(autoTradingAdminCommandService, never())
                .suspend(any());
    }

    @Test
    @DisplayName("ADMIN 권한이 아니면 AutoTrading 긴급 중지가 거부된다")
    void suspendAutoTradingWithoutAdminRole() {
        UUID autoTradingId = UUID.randomUUID();

        ServletException exception = assertThrows(
                ServletException.class,
                () -> mockMvc.perform(
                        patch(
                                "/api/v1/admin/trading/auto-tradings/{autoTradingId}/suspensions",
                                autoTradingId
                        )
                                .header("X-User-Role", "USER")
                                .contentType("application/json")
                                .content("""
                                    {
                                      "suspended": true
                                    }
                                    """)
                )
        );

        assertThat(exception.getCause())
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(autoTradingAdminCommandService);
    }

    @Test
    @DisplayName("X-User-Role 헤더가 없으면 AutoTrading 긴급 중지가 실패한다")
    void suspendAutoTradingWithoutRoleHeader() throws Exception {
        UUID autoTradingId = UUID.randomUUID();

        mockMvc.perform(
                        patch(
                                "/api/v1/admin/trading/auto-tradings/{autoTradingId}/suspensions",
                                autoTradingId
                        )
                                .contentType("application/json")
                                .content("""
                                    {
                                      "suspended": true
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(autoTradingAdminCommandService);
    }

    @Test
    @DisplayName("suspended 값이 없으면 요청이 실패한다")
    void suspendAutoTradingWithoutSuspendedValue() throws Exception {
        UUID autoTradingId = UUID.randomUUID();

        mockMvc.perform(
                        patch(
                                "/api/v1/admin/trading/auto-tradings/{autoTradingId}/suspensions",
                                autoTradingId
                        )
                                .header("X-User-Role", "ADMIN")
                                .contentType("application/json")
                                .content("""
                                    {}
                                    """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(autoTradingAdminCommandService);
    }

    @Test
    @DisplayName("ADMIN 권한이면 전체 AutoTrading을 긴급 중지할 수 있다")
    void suspendAllAutoTrading() throws Exception {
        // when & then
        mockMvc.perform(
                        patch(
                                "/api/v1/admin/trading/auto-tradings/suspensions"
                        )
                                .header("X-User-Role", "ADMIN")
                                .contentType("application/json")
                                .content("""
                                    {
                                      "suspended": true
                                    }
                                    """)
                )
                .andExpect(status().isOk());

        verify(autoTradingAdminCommandService)
                .suspendAll();

        verify(autoTradingAdminCommandService, never())
                .resumeAll();
    }

    @Test
    @DisplayName("ADMIN 권한이면 전체 AutoTrading 긴급 중지를 해제할 수 있다")
    void resumeAllAutoTrading() throws Exception {
        // when & then
        mockMvc.perform(
                        patch(
                                "/api/v1/admin/trading/auto-tradings/suspensions"
                        )
                                .header("X-User-Role", "ADMIN")
                                .contentType("application/json")
                                .content("""
                                    {
                                      "suspended": false
                                    }
                                    """)
                )
                .andExpect(status().isOk());

        verify(autoTradingAdminCommandService)
                .resumeAll();

        verify(autoTradingAdminCommandService, never())
                .suspendAll();
    }

    @Test
    @DisplayName("ADMIN 권한이 아니면 전체 AutoTrading 긴급 중지가 거부된다")
    void suspendAllAutoTradingWithoutAdminRole() {
        // when & then
        ServletException exception = assertThrows(
                ServletException.class,
                () -> mockMvc.perform(
                        patch(
                                "/api/v1/admin/trading/auto-tradings/suspensions"
                        )
                                .header("X-User-Role", "USER")
                                .contentType("application/json")
                                .content("""
                                    {
                                      "suspended": true
                                    }
                                    """)
                )
        );

        assertThat(exception.getCause())
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(autoTradingAdminCommandService);
    }

    @Test
    @DisplayName("전체 AutoTrading 제어 요청에 suspended 값이 없으면 실패한다")
    void updateGlobalSuspensionWithoutSuspended() throws Exception {
        mockMvc.perform(
                        patch(
                                "/api/v1/admin/trading/auto-tradings/suspensions"
                        )
                                .header("X-User-Role", "ADMIN")
                                .contentType("application/json")
                                .content("""
                                    {}
                                    """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(autoTradingAdminCommandService);
    }
}