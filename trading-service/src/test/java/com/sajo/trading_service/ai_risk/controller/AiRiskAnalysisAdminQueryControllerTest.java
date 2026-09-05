package com.sajo.trading_service.ai_risk.controller;
 
import com.sajo.common.config.CommonPageableAutoConfiguration;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiRiskAnalysisFailureHistoryItemResponse;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
 
@WebMvcTest(AiRiskAnalysisAdminQueryController.class)
@Import({
        GlobalExceptionHandler.class,
        CommonPageableAutoConfiguration.class
})
class AiRiskAnalysisAdminQueryControllerTest {
 
    @Autowired
    private MockMvc mockMvc;
 
    @MockitoBean
    private AiRiskAnalysisQueryService aiRiskAnalysisQueryService;
 
    @Test
    @DisplayName("ADMIN 권한이면 실패 이력 조회에 성공한다")
    void getFailureHistory_asAdmin_success() throws Exception {
        // given
        AiRiskAnalysisFailureHistoryItemResponse item = new AiRiskAnalysisFailureHistoryItemResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                AiAnalysisFailureType.LLM_API_ERROR,
                "실패 메시지",
                Instant.now()
        );
        Page<AiRiskAnalysisFailureHistoryItemResponse> page = new PageImpl<>(List.of(item));
 
        given(aiRiskAnalysisQueryService.getFailureHistory(isNull(), any())).willReturn(page);
 
        // when & then
        mockMvc.perform(
                        get("/api/v1/admin/ai/analyses/failures")
                                .header("X-User-Role", "ADMIN")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].failureType").value("LLM_API_ERROR"));
    }
 
    @Test
    @DisplayName("ADMIN이 아니면 403을 반환한다")
    void getFailureHistory_asNonAdmin_forbidden() throws Exception {
        // when & then
        mockMvc.perform(
                        get("/api/v1/admin/ai/analyses/failures")
                                .header("X-User-Role", "USER")
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON_0003"));
    }
 
    @Test
    @DisplayName("X-User-Role 헤더 없이 요청하면 401을 반환한다 (Gateway를 거치지 않은 요청)")
    void getFailureHistory_withoutRoleHeader_unauthorized() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/admin/ai/analyses/failures"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON_0002"));
    }
}
