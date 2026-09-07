package com.sajo.trading_service.ai_risk.controller;

import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiPromptVersionHistoryResponse;
import com.sajo.trading_service.ai_risk.domain.AiPromptKey;
import com.sajo.trading_service.ai_risk.domain.AiPromptStatus;
import com.sajo.trading_service.ai_risk.service.query.AiPromptVersionQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("unit")
@Tag("ai-risk")
@WebMvcTest(AiPromptVersionQueryController.class)
@Import(GlobalExceptionHandler.class)
class AiPromptVersionQueryControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiPromptVersionQueryService aiPromptVersionQueryService;

    @Test
    @DisplayName("관리자는 프롬프트 버전별 분석 통계를 조회할 수 있다")
    void getPromptVersions() throws Exception {

        AiPromptVersionHistoryResponse response =
                new AiPromptVersionHistoryResponse(
                        UUID.randomUUID(),
                        AiPromptKey.RISK_ANALYSIS,
                        "v1",
                        AiPromptStatus.ACTIVE,
                        Instant.now(),
                        null,
                        10L,
                        2L,
                        20.0,
                        Map.of()
                );

        given(aiPromptVersionQueryService.getPromptVersionHistories(any()))
                .willReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/v1/admin/ai/prompt-versions")
                        .header("X-User-Role", "ADMIN")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].version").value("v1"))
                .andExpect(jsonPath("$.data.content[0].totalCount").value(10))
                .andExpect(jsonPath("$.data.content[0].failedCount").value(2))
                .andExpect(jsonPath("$.data.content[0].failureRate").value(20.0));
    }

    @Test
    @DisplayName("관리자가 아니면 프롬프트 버전 이력을 조회할 수 없다")
    void getPromptVersionsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ai/prompt-versions")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("권한 헤더가 없으면 프롬프트 버전 이력 조회에 실패한다")
    void getPromptVersionsWithoutRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ai/prompt-versions"))
                .andExpect(status().isBadRequest());
    }
}