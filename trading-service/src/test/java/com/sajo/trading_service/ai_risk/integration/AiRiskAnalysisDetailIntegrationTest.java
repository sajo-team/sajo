package com.sajo.trading_service.ai_risk.integration;

import com.sajo.trading_service.ai_risk.domain.*;
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
@Tag("unit")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
public class AiRiskAnalysisDetailIntegrationTest {

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
    @DisplayName("완료된 AI 위험 분석 상세 조회에 성공한다")
    void getAnalysis_success() throws Exception {

        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();

        AiRiskAnalysis analysis = AiRiskAnalysis.create(
                userId,
                strategyId,
                backtestId
        );

        analysis.complete(
                RiskLevel.HIGH,
                "위험도가 높은 전략입니다.",
                List.of(
                        new RiskFactor(
                                RiskFactorType.MAX_DRAWDOWN,
                                "최대 낙폭이 높습니다."
                        )
                ),
                "백테스트 결과 최대 낙폭이 높아 위험도가 높습니다.",
                List.of("손절 기준을 조정해 주세요.")
        );

        AiRiskAnalysis savedAnalysis = repository.saveAndFlush(analysis);

        mockMvc.perform(
                        get("/api/v1/ai/analyses/{analysisId}", savedAnalysis.getId())
                                .header("X-User-Id", userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisId").value(savedAnalysis.getId().toString()))
                .andExpect(jsonPath("$.data.strategyId").value(strategyId.toString()))
                .andExpect(jsonPath("$.data.backtestId").value(backtestId.toString()))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.summary").value("위험도가 높은 전략입니다."))
                .andExpect(jsonPath("$.data.riskFactors[0].type").value("MAX_DRAWDOWN"))
                .andExpect(jsonPath("$.data.riskFactors[0].description").value("최대 낙폭이 높습니다."))
                .andExpect(jsonPath("$.data.reasoning").value("백테스트 결과 최대 낙폭이 높아 위험도가 높습니다."))
                .andExpect(jsonPath("$.data.recommendations[0]").value("손절 기준을 조정해 주세요."))
                .andExpect(jsonPath("$.data.failureType").isEmpty())
                .andExpect(jsonPath("$.data.message").value("AI 분석이 완료되었습니다."));
    }

    @Test
    @DisplayName("존재하지 않는 AI 위험 분석을 조회하면 실패한다")
    void getAnalysis_notFound() throws Exception {

        UUID userId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();

        mockMvc.perform(
                        get("/api/v1/ai/analyses/{analysisId}", analysisId)
                                .header("X-User-Id", userId)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AI_RISK_0008"))
                .andExpect(jsonPath("$.message").value("AI 위험 분석을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("다른 사용자의 AI 위험 분석은 조회할 수 없다")
    void getAnalysis_otherUser_notFound() throws Exception {

        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();

        AiRiskAnalysis analysis = AiRiskAnalysis.create(
                ownerId,
                strategyId,
                backtestId
        );

        AiRiskAnalysis savedAnalysis = repository.saveAndFlush(analysis);

        mockMvc.perform(
                        get("/api/v1/ai/analyses/{analysisId}", savedAnalysis.getId())
                                .header("X-User-Id", otherUserId)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AI_RISK_0008"))
                .andExpect(jsonPath("$.message").value("AI 위험 분석을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("실패한 AI 위험 분석 상세 조회 시 실패 정보를 반환한다")
    void getAnalysis_failed_success() throws Exception {

        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();

        AiRiskAnalysis analysis = AiRiskAnalysis.create(
                userId,
                strategyId,
                backtestId
        );

        analysis.fail(
                AiAnalysisFailureType.VALIDATION_ERROR,
                "위험 분석 응답 검증 실패"
        );

        AiRiskAnalysis savedAnalysis = repository.saveAndFlush(analysis);

        mockMvc.perform(
                        get("/api/v1/ai/analyses/{analysisId}", savedAnalysis.getId())
                                .header("X-User-Id", userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisId").value(savedAnalysis.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failureType").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.message").value("AI 분석 결과를 검증하는 중 오류가 발생했습니다."))
                .andExpect(jsonPath("$.data.riskLevel").isEmpty())
                .andExpect(jsonPath("$.data.summary").isEmpty());
    }
}


