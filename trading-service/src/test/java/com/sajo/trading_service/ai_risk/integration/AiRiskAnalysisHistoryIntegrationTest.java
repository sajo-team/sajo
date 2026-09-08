package com.sajo.trading_service.ai_risk.integration;


import com.sajo.trading_service.ai_risk.domain.AiRiskAnalysis;
import com.sajo.trading_service.ai_risk.domain.RiskFactor;
import com.sajo.trading_service.ai_risk.domain.RiskFactorType;
import com.sajo.trading_service.ai_risk.domain.RiskLevel;
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
public class AiRiskAnalysisHistoryIntegrationTest {

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
    @DisplayName("사용자의 AI 위험 분석 이력을 조회한다")
    void getAnalysisHistory_success() throws Exception {

        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        AiRiskAnalysis completed = AiRiskAnalysis.create(
                userId,
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        completed.complete(
                RiskLevel.HIGH,
                "위험도가 높은 전략입니다.",
                List.of(
                        new RiskFactor(
                                RiskFactorType.MAX_DRAWDOWN,
                                "최대 낙폭이 높습니다."
                        )
                ),
                "최대 낙폭이 높습니다.",
                List.of("손절 기준을 조정해 주세요.")
        );

        AiRiskAnalysis pending = AiRiskAnalysis.create(
                userId,
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        AiRiskAnalysis otherUserAnalysis = AiRiskAnalysis.create(
                otherUserId,
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        repository.saveAllAndFlush(
                List.of(completed, pending, otherUserAnalysis)
        );

        mockMvc.perform(
                        get("/api/v1/ai/analyses")
                                .header("X-User-Id", userId)
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @Test
    @DisplayName("AI 위험 분석 이력을 페이지 단위로 조회한다")
    void getAnalysisHistory_paging_success() throws Exception {

        UUID userId = UUID.randomUUID();

        for(int i = 0; i<11; i++){
            AiRiskAnalysis analysis = AiRiskAnalysis.create(
                    userId,
                    UUID.randomUUID(),
                    UUID.randomUUID()
            );

            repository.save(analysis);
        }

        repository.flush();

        mockMvc.perform(
                        get("/api/v1/ai/analyses")
                                .header("X-User-Id", userId)
                                .param("page", "0")
                                .param("size", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(10))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(11))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    @DisplayName("AI 위험 분석 이력이 없으면 빈 페이지를 반환한다")
    void getAnalysisHistory_empty() throws Exception {

        UUID userId = UUID.randomUUID();

        mockMvc.perform(
                        get("/api/v1/ai/analyses")
                                .header("X-User-Id", userId)
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

    @Test
    @DisplayName("AI 위험 분석 이력은 최신 생성순으로 조회한다")
    void getAnalysisHistory_defaultSort_createdAtDesc() throws Exception {

        UUID userId = UUID.randomUUID();

        AiRiskAnalysis first = repository.saveAndFlush(
                AiRiskAnalysis.create(
                        userId,
                        UUID.randomUUID(),
                        UUID.randomUUID()
                )
        );

        AiRiskAnalysis second = repository.saveAndFlush(
                AiRiskAnalysis.create(
                        userId,
                        UUID.randomUUID(),
                        UUID.randomUUID()
                )
        );

        AiRiskAnalysis third = repository.saveAndFlush(
                AiRiskAnalysis.create(
                        userId,
                        UUID.randomUUID(),
                        UUID.randomUUID()
                )
        );

        mockMvc.perform(
                        get("/api/v1/ai/analyses")
                                .header("X-User-Id", userId)
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andExpect(jsonPath("$.data.content[0].analysisId").value(third.getId().toString()))
                .andExpect(jsonPath("$.data.content[1].analysisId").value(second.getId().toString()))
                .andExpect(jsonPath("$.data.content[2].analysisId").value(first.getId().toString()));
    }
}
