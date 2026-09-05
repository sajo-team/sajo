package com.sajo.trading_service.ai_risk.controller;
 
import com.sajo.common.config.CommonPageableAutoConfiguration;
import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiRiskAnalysisDetailResponse;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiRiskAnalysisHistoryItemResponse;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisStatus;
import com.sajo.trading_service.ai_risk.domain.RiskLevel;
import com.sajo.trading_service.ai_risk.exception.AiRiskErrorCode;
import com.sajo.trading_service.ai_risk.service.query.AiRiskAnalysisQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
 
import java.time.Instant;
import java.util.List;
import java.util.UUID;
 
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
 
@WebMvcTest(AiRiskAnalysisQueryController.class)
@Import({
        GlobalExceptionHandler.class,
        CommonPageableAutoConfiguration.class
})
class AiRiskAnalysisQueryControllerTest {
 
    @Autowired
    private MockMvc mockMvc;
 
    @MockitoBean
    private AiRiskAnalysisQueryService aiRiskAnalysisQueryService;
 
    @Test
    @DisplayName("AI 위험 분석 상세 조회에 성공한다")
    void getAnalysis_success() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();
 
        AiRiskAnalysisDetailResponse response = new AiRiskAnalysisDetailResponse(
                analysisId,
                strategyId,
                backtestId,
                AiAnalysisStatus.COMPLETED,
                RiskLevel.LOW,
                "요약",
                List.of(),
                "근거",
                List.of("추천1"),
                null,
                "AI 분석이 완료되었습니다."
        );
 
        given(aiRiskAnalysisQueryService.getAnalysis(analysisId, userId)).willReturn(response);
 
        // when & then
        mockMvc.perform(
                        get("/api/v1/ai/analyses/{analysisId}", analysisId)
                                .header("X-User-Id", userId.toString())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.analysisId").value(analysisId.toString()))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }
 
    @Test
    @DisplayName("분석 결과를 찾을 수 없으면 404를 반환한다")
    void getAnalysis_notFound() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
 
        willThrow(new BusinessException(AiRiskErrorCode.ANALYSIS_NOT_FOUND))
                .given(aiRiskAnalysisQueryService).getAnalysis(analysisId, userId);
 
        // when & then
        mockMvc.perform(
                        get("/api/v1/ai/analyses/{analysisId}", analysisId)
                                .header("X-User-Id", userId.toString())
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AI_RISK_0008"));
    }
 
    @Test
    @DisplayName("AI 위험 분석 이력 조회에 성공한다")
    void getAnalysisHistory_success() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();
 
        AiRiskAnalysisHistoryItemResponse item = new AiRiskAnalysisHistoryItemResponse(
                analysisId,
                strategyId,
                backtestId,
                AiAnalysisStatus.COMPLETED,
                RiskLevel.LOW,
                "요약",
                Instant.now()
        );
 
        Page<AiRiskAnalysisHistoryItemResponse> page = new PageImpl<>(List.of(item));
 
        given(aiRiskAnalysisQueryService.getAnalysisHistory(eq(userId), any())).willReturn(page);
 
        // when & then
        mockMvc.perform(
                        get("/api/v1/ai/analyses")
                                .header("X-User-Id", userId.toString())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].analysisId").value(analysisId.toString()));
    }
 
    @Test
    @DisplayName("X-User-Id 헤더 없이 상세 조회를 요청하면 400을 반환한다 (Gateway를 거치지 않은 요청)")
    void getAnalysis_withoutUserIdHeader() throws Exception {
        // given
        UUID analysisId = UUID.randomUUID();
 
        // when & then
        mockMvc.perform(get("/api/v1/ai/analyses/{analysisId}", analysisId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON_0001"));
    }
}
