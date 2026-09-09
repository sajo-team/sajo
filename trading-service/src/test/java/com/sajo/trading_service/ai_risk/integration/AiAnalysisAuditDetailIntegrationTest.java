package com.sajo.trading_service.ai_risk.integration;

import com.sajo.trading_service.ai_risk.document.AiAnalysisHistory;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisStatus;
import com.sajo.trading_service.ai_risk.repository.query.AiAnalysisHistoryQueryRepository;
import org.bson.UuidRepresentation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag("integration")
@Tag("ai-risk")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
public class AiAnalysisAuditDetailIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        registry.add("spring.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiAnalysisHistoryQueryRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("관리자는 AI 분석 Audit 상세 이력을 조회할 수 있다")
    void getAuditDetail_success() throws Exception {

        UUID analysisId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();

        AiAnalysisHistory history = AiAnalysisHistory.builder()
                .analysisId(analysisId)
                .userId(userId)
                .strategyId(strategyId)
                .backtestId(backtestId)
                .requestSnapshot(
                        Map.of(
                                "stockCode", "005930",
                                "strategyName", "삼성전자 전략"
                        )
                )
                .prompt(
                        new AiAnalysisHistory.PromptSnapshot(
                                "v1",
                                "AI 위험 분석 프롬프트"
                        )
                )
                .response(
                        new AiAnalysisHistory.ResponseSnapshot(
                                "{\"riskLevel\":\"HIGH\"}"
                        )
                )
                .validation(
                        new AiAnalysisHistory.ValidationSnapshot(
                                true,
                                true,
                                List.of()
                        )
                )
                .metadata(
                        new AiAnalysisHistory.MetadataSnapshot(
                                "gpt-4o-mini",
                                1200L
                        )
                )
                .result(
                        new AiAnalysisHistory.ResultSnapshot(
                                AiAnalysisStatus.COMPLETED,
                                null
                        )
                )
                .build();

        repository.save(history);

        mockMvc.perform(
                        get(
                                "/api/v1/admin/ai/analyses/{analysisId}/audit",
                                analysisId
                        )
                                .header("X-User-Role", "ADMIN")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisId")
                        .value(analysisId.toString()))
                .andExpect(jsonPath("$.data.userId")
                        .value(userId.toString()))
                .andExpect(jsonPath("$.data.strategyId")
                        .value(strategyId.toString()))
                .andExpect(jsonPath("$.data.backtestId")
                        .value(backtestId.toString()))

                .andExpect(jsonPath("$.data.requestSnapshot.stockCode")
                        .value("005930"))
                .andExpect(jsonPath("$.data.requestSnapshot.strategyName")
                        .value("삼성전자 전략"))

                .andExpect(jsonPath("$.data.prompt.version")
                        .value("v1"))
                .andExpect(jsonPath("$.data.prompt.content")
                        .value("AI 위험 분석 프롬프트"))

                .andExpect(jsonPath("$.data.response.rawResponse")
                        .value("{\"riskLevel\":\"HIGH\"}"))

                .andExpect(jsonPath("$.data.validation.structureValid")
                        .value(true))
                .andExpect(jsonPath("$.data.validation.contentValid")
                        .value(true))
                .andExpect(jsonPath("$.data.validation.errors")
                        .isEmpty())

                .andExpect(jsonPath("$.data.metadata.model")
                        .value("gpt-4o-mini"))
                .andExpect(jsonPath("$.data.metadata.latencyMs")
                        .value(1200))

                .andExpect(jsonPath("$.data.result.status")
                        .value("COMPLETED"))
                .andExpect(jsonPath("$.data.result.failureType")
                        .isEmpty())

                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    @DisplayName("존재하지 않는 Audit 이력을 조회하면 404를 반환한다")
    void getAuditDetail_notFound() throws Exception {

        UUID analysisId = UUID.randomUUID();

        mockMvc.perform(
                        get(
                                "/api/v1/admin/ai/analyses/{analysisId}/audit",
                                analysisId
                        )
                                .header("X-User-Role", "ADMIN")
                )
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("관리자가 아닌 사용자가 Audit 상세 이력을 조회하면 403을 반환한다")
    void getAuditDetail_forbidden() throws Exception {

        UUID analysisId = UUID.randomUUID();

        mockMvc.perform(
                        get(
                                "/api/v1/admin/ai/analyses/{analysisId}/audit",
                                analysisId
                        )
                                .header("X-User-Role", "USER")
                )
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("실패한 AI 분석의 Audit 이력에서 검증 오류와 실패 유형을 조회할 수 있다")
    void getAuditDetail_failedAnalysis_success() throws Exception {

        UUID analysisId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();

        AiAnalysisHistory history = AiAnalysisHistory.builder()
                .analysisId(analysisId)
                .userId(userId)
                .strategyId(strategyId)
                .backtestId(backtestId)
                .requestSnapshot(
                        Map.of(
                                "stockCode", "005930",
                                "strategyName", "삼성전자 전략"
                        )
                )
                .prompt(
                        new AiAnalysisHistory.PromptSnapshot(
                                "v1",
                                "AI 위험 분석 프롬프트"
                        )
                )
                .response(
                        new AiAnalysisHistory.ResponseSnapshot(
                                "invalid response"
                        )
                )
                .validation(
                        new AiAnalysisHistory.ValidationSnapshot(
                                false,
                                false,
                                List.of("응답 형식이 올바르지 않습니다")
                        )
                )
                .metadata(
                        new AiAnalysisHistory.MetadataSnapshot(
                                "gpt-4o-mini",
                                1500L
                        )
                )
                .result(
                        new AiAnalysisHistory.ResultSnapshot(
                                AiAnalysisStatus.FAILED,
                                AiAnalysisFailureType.VALIDATION_ERROR
                        )
                )
                .build();

        repository.save(history);

        mockMvc.perform(
                        get(
                                "/api/v1/admin/ai/analyses/{analysisId}/audit",
                                analysisId
                        )
                                .header("X-User-Role", "ADMIN")
                )
                .andExpect(status().isOk())

                .andExpect(jsonPath("$.data.analysisId")
                        .value(analysisId.toString()))

                .andExpect(jsonPath("$.data.response.rawResponse")
                        .value("invalid response"))

                .andExpect(jsonPath("$.data.validation.structureValid")
                        .value(false))
                .andExpect(jsonPath("$.data.validation.contentValid")
                        .value(false))
                .andExpect(jsonPath("$.data.validation.errors[0]")
                        .value("응답 형식이 올바르지 않습니다"))

                .andExpect(jsonPath("$.data.result.status")
                        .value("FAILED"))
                .andExpect(jsonPath("$.data.result.failureType")
                        .value("VALIDATION_ERROR"))

                .andExpect(jsonPath("$.data.metadata.model")
                        .value("gpt-4o-mini"))
                .andExpect(jsonPath("$.data.metadata.latencyMs")
                        .value(1500));
    }
}
