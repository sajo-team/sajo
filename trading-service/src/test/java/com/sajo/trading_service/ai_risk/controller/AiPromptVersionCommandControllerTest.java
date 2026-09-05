package com.sajo.trading_service.ai_risk.controller;
 
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.trading_service.ai_risk.controller.dto.request.AiPromptVersionCreateRequest;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiPromptVersionCreateResponse;
import com.sajo.trading_service.ai_risk.domain.AiPromptKey;
import com.sajo.trading_service.ai_risk.domain.AiPromptStatus;
import com.sajo.trading_service.ai_risk.service.command.AiPromptVersionCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
 
import java.time.Instant;
import java.util.UUID;
 
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
 
@WebMvcTest(AiPromptVersionCommandController.class)
@Import(GlobalExceptionHandler.class)
class AiPromptVersionCommandControllerTest {
 
    @Autowired
    private MockMvc mockMvc;
 
    @Autowired
    private ObjectMapper objectMapper;
 
    @MockitoBean
    private AiPromptVersionCommandService promptVersionCommandService;
 
    @Test
    @DisplayName("ADMIN 권한이면 프롬프트 버전 생성에 성공한다")
    void create_asAdmin_success() throws Exception {
        // given
        AiPromptVersionCreateRequest request =
                new AiPromptVersionCreateRequest(AiPromptKey.RISK_ANALYSIS, "프롬프트 내용", "변경 요약");
        AiPromptVersionCreateResponse response = new AiPromptVersionCreateResponse(
                UUID.randomUUID(), AiPromptKey.RISK_ANALYSIS, "v1", AiPromptStatus.ACTIVE, Instant.now());
 
        given(promptVersionCommandService.create(any(AiPromptVersionCreateRequest.class))).willReturn(response);
 
        // when & then
        mockMvc.perform(
                        post("/api/v1/admin/ai/prompt-versions")
                                .header("X-User-Role", "ADMIN")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.version").value("v1"));
    }
 
    @Test
    @DisplayName("ADMIN이 아니면 403을 반환한다")
    void create_asNonAdmin_forbidden() throws Exception {
        // given
        AiPromptVersionCreateRequest request =
                new AiPromptVersionCreateRequest(AiPromptKey.RISK_ANALYSIS, "프롬프트 내용", "변경 요약");
 
        // when & then
        mockMvc.perform(
                        post("/api/v1/admin/ai/prompt-versions")
                                .header("X-User-Role", "USER")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON_0003"));
    }
 
    @Test
    @DisplayName("X-User-Role 헤더 없이 요청하면 401을 반환한다 (Gateway를 거치지 않은 요청)")
    void create_withoutRoleHeader_unauthorized() throws Exception {
        // given
        AiPromptVersionCreateRequest request =
                new AiPromptVersionCreateRequest(AiPromptKey.RISK_ANALYSIS, "프롬프트 내용", "변경 요약");
 
        // when & then
        mockMvc.perform(
                        post("/api/v1/admin/ai/prompt-versions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON_0002"));
    }
}
