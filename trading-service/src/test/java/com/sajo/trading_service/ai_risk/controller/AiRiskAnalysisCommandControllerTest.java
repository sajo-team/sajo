package com.sajo.trading_service.ai_risk.controller;
 
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.trading_service.ai_risk.controller.dto.request.AiRiskAnalysisCreateRequest;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiRiskAnalysisCreateResponse;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisStatus;
import com.sajo.trading_service.ai_risk.exception.AiRiskErrorCode;
import com.sajo.trading_service.ai_risk.service.command.AiRiskAnalysisCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
 
import java.util.UUID;
 
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
 
@WebMvcTest(AiRiskAnalysisCommandController.class)
@Import(GlobalExceptionHandler.class)
class AiRiskAnalysisCommandControllerTest {
 
    @Autowired
    private MockMvc mockMvc;
 
    @Autowired
    private ObjectMapper objectMapper;
 
    @MockitoBean
    private AiRiskAnalysisCommandService aiRiskAnalysisCommandService;
 
    @Test
    @DisplayName("AI 위험 분석 요청에 성공하면 201과 생성된 분석 정보를 반환한다")
    void createAnalysis_success() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
 
        AiRiskAnalysisCreateRequest request = new AiRiskAnalysisCreateRequest(strategyId, backtestId);
        AiRiskAnalysisCreateResponse response =
                new AiRiskAnalysisCreateResponse(analysisId, AiAnalysisStatus.PENDING);
 
        given(aiRiskAnalysisCommandService.create(eq(userId), any(AiRiskAnalysisCreateRequest.class)))
                .willReturn(response);
 
        // when & then
        mockMvc.perform(
                        post("/api/v1/ai/analyses")
                                .header("X-User-Id", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.analysisId").value(analysisId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }
 
    @Test
    @DisplayName("전략에 접근 권한이 없으면 403을 반환한다")
    void createAnalysis_strategyAccessDenied() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        AiRiskAnalysisCreateRequest request =
                new AiRiskAnalysisCreateRequest(UUID.randomUUID(), UUID.randomUUID());
 
        willThrow(new BusinessException(AiRiskErrorCode.STRATEGY_ACCESS_DENIED))
                .given(aiRiskAnalysisCommandService)
                .create(eq(userId), any(AiRiskAnalysisCreateRequest.class));
 
        // when & then
        mockMvc.perform(
                        post("/api/v1/ai/analyses")
                                .header("X-User-Id", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AI_RISK_0004"));
    }
 
    @Test
    @DisplayName("X-User-Id 헤더 없이 요청하면 400을 반환한다 (Gateway를 거치지 않은 요청)")
    void createAnalysis_withoutUserIdHeader() throws Exception {
        // given
        AiRiskAnalysisCreateRequest request =
                new AiRiskAnalysisCreateRequest(UUID.randomUUID(), UUID.randomUUID());
 
        // when & then
        mockMvc.perform(
                        post("/api/v1/ai/analyses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON_0001"));
    }
}
