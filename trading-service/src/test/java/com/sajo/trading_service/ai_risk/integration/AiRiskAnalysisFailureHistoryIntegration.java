package com.sajo.trading_service.ai_risk.integration;

import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import com.sajo.trading_service.ai_risk.domain.AiRiskAnalysis;
import com.sajo.trading_service.ai_risk.repository.command.AiRiskAnalysisCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

@Tag("integration")
@Tag("ai-risk")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
public class AiRiskAnalysisFailureHistoryIntegration {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiRiskAnalysisCommandRepository repository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("관리자는 AI 위험 분석 실패 이력을 조회할 수 있다")
    void getFailureHistory_success() throws Exception {

        UUID userId = UUID.randomUUID();

        AiRiskAnalysis validationFailed = AiRiskAnalysis.create(
                userId,
                UUID.randomUUID(),
                UUID.randomUUID()
        );
        validationFailed.fail(
                AiAnalysisFailureType.VALIDATION_ERROR,
                "응답 검증에 실패했습니다."
        );

        AiRiskAnalysis llmFailed = AiRiskAnalysis.create(
                userId,
                UUID.randomUUID(),
                UUID.randomUUID()
        );
        llmFailed.fail(
                AiAnalysisFailureType.LLM_API_ERROR,
                "LLM API 호출에 실패했습니다."
        );

        AiRiskAnalysis pending = AiRiskAnalysis.create(
                userId,
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        repository.saveAllAndFlush(
                List.of(validationFailed, llmFailed, pending)
        );

        mockMvc.perform(
                        get("/api/v1/admin/ai/analyses/failures")
                                .header("X-User-Role", "ADMIN")
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    @DisplayName("관리자는 실패 유형으로 AI 위험 분석 실패 이력을 필터링할 수 있다")
    void getFailureHistory_filterByFailureType_success() throws Exception {

        UUID userId = UUID.randomUUID();

        AiRiskAnalysis validationFailed = AiRiskAnalysis.create(
                userId,
                UUID.randomUUID(),
                UUID.randomUUID()
        );
        validationFailed.fail(
                AiAnalysisFailureType.VALIDATION_ERROR,
                "응답 검증에 실패했습니다."
        );

        AiRiskAnalysis llmFailed = AiRiskAnalysis.create(
                userId,
                UUID.randomUUID(),
                UUID.randomUUID()
        );
        llmFailed.fail(
                AiAnalysisFailureType.LLM_API_ERROR,
                "LLM API 호출에 실패했습니다."
        );

        repository.saveAllAndFlush(
                List.of(validationFailed, llmFailed)
        );

        mockMvc.perform(
                        get("/api/v1/admin/ai/analyses/failures")
                                .header("X-User-Role", "ADMIN")
                                .param("failureType", "VALIDATION_ERROR")
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1))

                .andExpect(jsonPath("$.data.content[0].analysisId").value(validationFailed.getId().toString()))
                .andExpect(jsonPath("$.data.content[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.content[0].failureType").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.content[0].failureMessage").value("응답 검증에 실패했습니다."));
    }

    @Test
    @DisplayName("관리자가 아니면 AI 위험 분석 실패 이력을 조회할 수 없다")
    void getFailureHistory_forbidden() throws Exception {

        mockMvc.perform(
                        get("/api/v1/admin/ai/analyses/failures")
                                .header("X-User-Role", "USER")
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AI 위험 분석 실패 이력이 없으면 빈 페이지를 반환한다")
    void getFailureHistory_empty() throws Exception {

        UUID userId = UUID.randomUUID();

        AiRiskAnalysis pending = AiRiskAnalysis.create(
                userId,
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        repository.saveAndFlush(pending);

        mockMvc.perform(
                        get("/api/v1/admin/ai/analyses/failures")
                                .header("X-User-Role", "ADMIN")
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0));
    }


}
