package com.sajo.trading_service.ai_risk.integration;

import com.sajo.trading_service.ai_risk.document.AiAnalysisHistory;
import com.sajo.trading_service.ai_risk.domain.*;
import com.sajo.trading_service.ai_risk.repository.command.AiPromptVersionCommandRepository;
import com.sajo.trading_service.ai_risk.repository.query.AiAnalysisHistoryQueryRepository;
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
import org.testcontainers.mongodb.MongoDBContainer;
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
public class AiPromptVersionHistoryIntegrationTest {

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
    private AiPromptVersionCommandRepository promptRepository;

    @Autowired
    private AiAnalysisHistoryQueryRepository historyRepository;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        promptRepository.deleteAll();
    }

    @Test
    @DisplayName("관리자는 프롬프트 버전별 AI 분석 성공 및 실패 통계를 조회할 수 있다")
    void getPromptVersionHistory_success() throws Exception {

        AiPromptVersion prompt = AiPromptVersion.create(
                AiPromptKey.RISK_ANALYSIS,
                "v1",
                "AI 위험 분석 프롬프트",
                "최초 등록"
        );

        promptRepository.saveAndFlush(prompt);

        historyRepository.save(
                AiAnalysisHistory.builder()
                        .analysisId(UUID.randomUUID())
                        .userId(UUID.randomUUID())
                        .strategyId(UUID.randomUUID())
                        .backtestId(UUID.randomUUID())
                        .prompt(
                                new AiAnalysisHistory.PromptSnapshot(
                                        "v1",
                                        "AI 위험 분석 프롬프트"
                                )
                        )
                        .result(
                                new AiAnalysisHistory.ResultSnapshot(
                                        AiAnalysisStatus.COMPLETED,
                                        null
                                )
                        )
                        .build()
        );

        historyRepository.save(
                AiAnalysisHistory.builder()
                        .analysisId(UUID.randomUUID())
                        .userId(UUID.randomUUID())
                        .strategyId(UUID.randomUUID())
                        .backtestId(UUID.randomUUID())
                        .prompt(
                                new AiAnalysisHistory.PromptSnapshot(
                                        "v1",
                                        "AI 위험 분석 프롬프트"
                                )
                        )
                        .result(
                                new AiAnalysisHistory.ResultSnapshot(
                                        AiAnalysisStatus.FAILED,
                                        AiAnalysisFailureType.VALIDATION_ERROR
                                )
                        )
                        .build()
        );

        mockMvc.perform(
                        get("/api/v1/admin/ai/prompt-versions")
                                .header("X-User-Role", "ADMIN")
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))

                .andExpect(jsonPath("$.data.content[0].promptKey")
                        .value("RISK_ANALYSIS"))
                .andExpect(jsonPath("$.data.content[0].version")
                        .value("v1"))
                .andExpect(jsonPath("$.data.content[0].status")
                        .value("ACTIVE"))

                .andExpect(jsonPath("$.data.content[0].totalCount")
                        .value(2))
                .andExpect(jsonPath("$.data.content[0].failedCount")
                        .value(1))
                .andExpect(jsonPath("$.data.content[0].failureRate")
                        .value(50.0))
                .andExpect(jsonPath(
                        "$.data.content[0].failureTypeCounts.VALIDATION_ERROR"
                ).value(1));
    }

    @Test
    @DisplayName("프롬프트 버전별 실패 유형을 각각 집계한다")
    void getPromptVersionHistory_failureTypeCounts_success() throws Exception {

        AiPromptVersion prompt = AiPromptVersion.create(
                AiPromptKey.RISK_ANALYSIS,
                "v1",
                "AI 위험 분석 프롬프트",
                "최초 등록"
        );

        promptRepository.saveAndFlush(prompt);

        // VALIDATION_ERROR 2건
        for (int i = 0; i < 2; i++) {
            historyRepository.save(
                    AiAnalysisHistory.builder()
                            .analysisId(UUID.randomUUID())
                            .userId(UUID.randomUUID())
                            .strategyId(UUID.randomUUID())
                            .backtestId(UUID.randomUUID())
                            .prompt(
                                    new AiAnalysisHistory.PromptSnapshot(
                                            "v1",
                                            "AI 위험 분석 프롬프트"
                                    )
                            )
                            .result(
                                    new AiAnalysisHistory.ResultSnapshot(
                                            AiAnalysisStatus.FAILED,
                                            AiAnalysisFailureType.VALIDATION_ERROR
                                    )
                            )
                            .build()
            );
        }

        // LLM_API_ERROR 1건
        historyRepository.save(
                AiAnalysisHistory.builder()
                        .analysisId(UUID.randomUUID())
                        .userId(UUID.randomUUID())
                        .strategyId(UUID.randomUUID())
                        .backtestId(UUID.randomUUID())
                        .prompt(
                                new AiAnalysisHistory.PromptSnapshot(
                                        "v1",
                                        "AI 위험 분석 프롬프트"
                                )
                        )
                        .result(
                                new AiAnalysisHistory.ResultSnapshot(
                                        AiAnalysisStatus.FAILED,
                                        AiAnalysisFailureType.LLM_API_ERROR
                                )
                        )
                        .build()
        );

        mockMvc.perform(
                        get("/api/v1/admin/ai/prompt-versions")
                                .header("X-User-Role", "ADMIN")
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].totalCount")
                        .value(3))
                .andExpect(jsonPath("$.data.content[0].failedCount")
                        .value(3))
                .andExpect(jsonPath("$.data.content[0].failureRate")
                        .value(100.0))
                .andExpect(jsonPath(
                        "$.data.content[0].failureTypeCounts.VALIDATION_ERROR"
                ).value(2))
                .andExpect(jsonPath(
                        "$.data.content[0].failureTypeCounts.LLM_API_ERROR"
                ).value(1));
    }

    @Test
    @DisplayName("프롬프트 버전별로 AI 분석 통계를 분리하여 조회한다")
    void getPromptVersionHistory_separatesStatisticsByVersion() throws Exception {

        AiPromptVersion v1 = AiPromptVersion.create(
                AiPromptKey.RISK_ANALYSIS,
                "v1",
                "기존 프롬프트",
                "최초 등록"
        );
        v1.retire();

        AiPromptVersion v2 = AiPromptVersion.create(
                AiPromptKey.RISK_ANALYSIS,
                "v2",
                "개선된 프롬프트",
                "프롬프트 개선"
        );

        promptRepository.saveAllAndFlush(List.of(v1, v2));

        historyRepository.save(
                AiAnalysisHistory.builder()
                        .analysisId(UUID.randomUUID())
                        .userId(UUID.randomUUID())
                        .strategyId(UUID.randomUUID())
                        .backtestId(UUID.randomUUID())
                        .prompt(new AiAnalysisHistory.PromptSnapshot(
                                "v1",
                                "기존 프롬프트"
                        ))
                        .result(new AiAnalysisHistory.ResultSnapshot(
                                AiAnalysisStatus.COMPLETED,
                                null
                        ))
                        .build()
        );

        historyRepository.save(
                AiAnalysisHistory.builder()
                        .analysisId(UUID.randomUUID())
                        .userId(UUID.randomUUID())
                        .strategyId(UUID.randomUUID())
                        .backtestId(UUID.randomUUID())
                        .prompt(new AiAnalysisHistory.PromptSnapshot(
                                "v1",
                                "기존 프롬프트"
                        ))
                        .result(new AiAnalysisHistory.ResultSnapshot(
                                AiAnalysisStatus.FAILED,
                                AiAnalysisFailureType.VALIDATION_ERROR
                        ))
                        .build()
        );

        historyRepository.save(
                AiAnalysisHistory.builder()
                        .analysisId(UUID.randomUUID())
                        .userId(UUID.randomUUID())
                        .strategyId(UUID.randomUUID())
                        .backtestId(UUID.randomUUID())
                        .prompt(new AiAnalysisHistory.PromptSnapshot(
                                "v2",
                                "개선된 프롬프트"
                        ))
                        .result(new AiAnalysisHistory.ResultSnapshot(
                                AiAnalysisStatus.COMPLETED,
                                null
                        ))
                        .build()
        );

        mockMvc.perform(
                        get("/api/v1/admin/ai/prompt-versions")
                                .header("X-User-Role", "ADMIN")
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))

                .andExpect(jsonPath(
                        "$.data.content[?(@.version == 'v1')].totalCount"
                ).value(2))
                .andExpect(jsonPath(
                        "$.data.content[?(@.version == 'v1')].failedCount"
                ).value(1))
                .andExpect(jsonPath(
                        "$.data.content[?(@.version == 'v1')].failureRate"
                ).value(50.0))

                .andExpect(jsonPath(
                        "$.data.content[?(@.version == 'v2')].totalCount"
                ).value(1))
                .andExpect(jsonPath(
                        "$.data.content[?(@.version == 'v2')].failedCount"
                ).value(0))
                .andExpect(jsonPath(
                        "$.data.content[?(@.version == 'v2')].failureRate"
                ).value(0.0));
    }

    @Test
    @DisplayName("분석 이력이 없는 프롬프트 버전은 통계를 0으로 반환한다")
    void getPromptVersionHistory_withoutAnalysisHistory() throws Exception {

        AiPromptVersion prompt = AiPromptVersion.create(
                AiPromptKey.RISK_ANALYSIS,
                "v1",
                "AI 위험 분석 프롬프트",
                "최초 등록"
        );

        promptRepository.saveAndFlush(prompt);

        mockMvc.perform(
                        get("/api/v1/admin/ai/prompt-versions")
                                .header("X-User-Role", "ADMIN")
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].version")
                        .value("v1"))
                .andExpect(jsonPath("$.data.content[0].totalCount")
                        .value(0))
                .andExpect(jsonPath("$.data.content[0].failedCount")
                        .value(0))
                .andExpect(jsonPath("$.data.content[0].failureRate")
                        .value(0.0))
                .andExpect(jsonPath("$.data.content[0].failureTypeCounts")
                        .isEmpty());
    }

    @Test
    @DisplayName("관리자가 아닌 사용자는 프롬프트 버전 이력을 조회할 수 없다")
    void getPromptVersionHistory_forbidden() throws Exception {

        AiPromptVersion prompt = AiPromptVersion.create(
                AiPromptKey.RISK_ANALYSIS,
                "v1",
                "AI 위험 분석 프롬프트",
                "최초 등록"
        );

        promptRepository.saveAndFlush(prompt);

        mockMvc.perform(
                        get("/api/v1/admin/ai/prompt-versions")
                                .header("X-User-Role", "USER")
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("프롬프트 버전 이력을 페이지 단위로 조회한다")
    void getPromptVersionHistory_paging() throws Exception {

        for (int i = 1; i <= 11; i++) {

            promptRepository
                    .findByPromptKeyAndStatus(
                            AiPromptKey.RISK_ANALYSIS,
                            AiPromptStatus.ACTIVE
                    )
                    .ifPresent(prompt -> {
                        prompt.retire();
                        promptRepository.saveAndFlush(prompt);
                    });

            AiPromptVersion prompt = AiPromptVersion.create(
                    AiPromptKey.RISK_ANALYSIS,
                    "v" + i,
                    "프롬프트 " + i,
                    "버전 " + i
            );

            promptRepository.saveAndFlush(prompt);
        }

        mockMvc.perform(
                        get("/api/v1/admin/ai/prompt-versions")
                                .header("X-User-Role", "ADMIN")
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(11))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10));
    }
}
