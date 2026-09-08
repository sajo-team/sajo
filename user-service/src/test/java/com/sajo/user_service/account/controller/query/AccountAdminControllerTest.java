package com.sajo.user_service.account.controller.query;

import com.sajo.common.config.CommonPageableAutoConfiguration;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.user_service.account.controller.dto.response.TokenEventResponse;
import com.sajo.user_service.account.controller.dto.response.TokenStatusResponse;
import com.sajo.user_service.account.domain.EventType;
import com.sajo.user_service.account.domain.KisTokenType;
import com.sajo.user_service.account.service.query.KisTokenLogQueryService;
import com.sajo.user_service.config.MethodSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountAdminController.class)
@Import({
        GlobalExceptionHandler.class,
        CommonPageableAutoConfiguration.class,
        MethodSecurityConfig.class
})
class AccountAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KisTokenLogQueryService kisTokenLogQueryService;

    @Test
    @DisplayName("ADMIN 권한이면 토큰 발급 상태 목록 조회에 성공한다")
    @WithMockUser(roles = "ADMIN")
    void getTokenStatuses_asAdmin_success() throws Exception {
        // given
        TokenStatusResponse item = new TokenStatusResponse(
                UUID.randomUUID(), KisTokenType.ACCESS_TOKEN, EventType.TOKEN_ISSUE_SUCCESS,
                null, null, Instant.now());
        Page<TokenStatusResponse> page = new PageImpl<>(List.of(item));
        given(kisTokenLogQueryService.getTokenStatuses(any())).willReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/admin/accounts/token-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].tokenType").value("ACCESS_TOKEN"));
    }

    @Test
    @DisplayName("ADMIN이 아니면 토큰 발급 상태 목록 조회 시 403을 반환한다")
    @WithMockUser(roles = "USER")
    void getTokenStatuses_asNonAdmin_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/accounts/token-status"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("인증 정보 없이 요청하면 토큰 발급 상태 목록 조회 시 403을 반환한다 (Gateway를 거치지 않은 요청)")
    @WithAnonymousUser
    void getTokenStatuses_withoutAuthentication_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/accounts/token-status"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("ADMIN 권한이면 특정 사용자 토큰 이력 조회에 성공한다")
    @WithMockUser(roles = "ADMIN")
    void getTokenEventHistory_asAdmin_success() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        TokenEventResponse item = new TokenEventResponse(
                KisTokenType.APPROVAL_KEY, EventType.TOKEN_ISSUE_FAILED, "EGW00133", "1분당 1회 제한", Instant.now());
        Page<TokenEventResponse> page = new PageImpl<>(List.of(item));
        given(kisTokenLogQueryService.getTokenEventHistory(any(), any())).willReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/admin/accounts/{userId}/token-status/history", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].errorCode").value("EGW00133"));
    }

    @Test
    @DisplayName("ADMIN이 아니면 특정 사용자 토큰 이력 조회 시 403을 반환한다")
    @WithMockUser(roles = "USER")
    void getTokenEventHistory_asNonAdmin_forbidden() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/admin/accounts/{userId}/token-status/history", userId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("인증 정보 없이 요청하면 특정 사용자 토큰 이력 조회 시 403을 반환한다")
    @WithAnonymousUser
    void getTokenEventHistory_withoutAuthentication_forbidden() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/admin/accounts/{userId}/token-status/history", userId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }
}
