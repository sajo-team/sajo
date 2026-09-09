package com.sajo.trading_service.ai_risk.integration;

import com.sajo.trading_service.ai_risk.client.backtest.BacktestFeignClient;
import com.sajo.trading_service.ai_risk.client.backtest.dto.BacktestInternalResponse;
import com.sajo.trading_service.ai_risk.client.strategy.StrategyFeignClient;
import com.sajo.trading_service.ai_risk.client.strategy.dto.StrategyInternalResponse;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisStatus;
import com.sajo.trading_service.ai_risk.domain.AiRiskAnalysis;
import com.sajo.trading_service.ai_risk.repository.command.AiRiskAnalysisCommandRepository;
import com.sajo.trading_service.ai_risk.service.processor.AiRiskAnalysisAsyncProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@Tag("ai-risk")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
public class AiRiskAnalysisCreateIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AiRiskAnalysisCommandRepository aiRiskAnalysisCommandRepository;

    @MockitoBean
    private StrategyFeignClient strategyFeignClient;

    @MockitoBean
    private BacktestFeignClient backtestFeignClient;

    @MockitoBean
    private AiRiskAnalysisAsyncProcessor asyncProcessor;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @BeforeEach
    void setUp() {
        aiRiskAnalysisCommandRepository.deleteAll();
    }

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID STRATEGY_ID = UUID.randomUUID();
    private static final UUID BACKTEST_ID = UUID.randomUUID();

    @Test
    @DisplayName("AI 위험 분석 요청 시 PENDING 상태의 분석이 저장된다.")
    void createAnalysis_success() throws Exception {
        StrategyInternalResponse strategy = new StrategyInternalResponse(
                STRATEGY_ID,
                USER_ID,
                "005930",
                "삼성전자 전략",
                70000L,
                80000L,
                new BigDecimal("5.0"),
                new BigDecimal("10.0"),
                1_000_000L,
                100_000L,
                new BigDecimal("10.0"),
                new BigDecimal("1.0"),
                new BigDecimal("10.0"),
                "ACTIVE"
        );

        BacktestInternalResponse backtest = new BacktestInternalResponse(
                BACKTEST_ID,
                STRATEGY_ID,
                USER_ID,
                "005930",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                10_000_000L,
                "COMPLETED",
                new BigDecimal("12.5"),
                new BigDecimal("8.3"),
                new BigDecimal("60.0"),
                20,
                3
        );

        given(strategyFeignClient.getStrategy(STRATEGY_ID)).willReturn(strategy);
        given(backtestFeignClient.getBacktest(BACKTEST_ID)).willReturn(backtest);

        mockMvc.perform(post("/api/v1/ai/analyses")
                .header("X-User-Id", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "strategyId": "%s",
                            "backtestId": "%s"
                        }
                        """.formatted(STRATEGY_ID, BACKTEST_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        List<AiRiskAnalysis> analyses = aiRiskAnalysisCommandRepository.findAll();

        assertThat(analyses).hasSize(1);

        AiRiskAnalysis savedAnalysis = analyses.get(0);

        assertThat(savedAnalysis.getUserId()).isEqualTo(USER_ID);
        assertThat(savedAnalysis.getStrategyId()).isEqualTo(STRATEGY_ID);
        assertThat(savedAnalysis.getBacktestId()).isEqualTo(BACKTEST_ID);
        assertThat(savedAnalysis.getStatus()).isEqualTo(AiAnalysisStatus.PENDING);
    }

    @Test
    @DisplayName("다른 사용자의 전략으로 AI 위험 분석을 요청하면 실패한다.")
    void createAnalysis_strategyAccessDenied() throws Exception {
        UUID otherUserId = UUID.randomUUID();

        StrategyInternalResponse strategy = new StrategyInternalResponse(
                STRATEGY_ID,
                otherUserId,
                "005930",
                "삼성전자 전략",
                70000L,
                80000L,
                new BigDecimal("5.0"),
                new BigDecimal("10.0"),
                1_000_000L,
                100_000L,
                new BigDecimal("10.0"),
                new BigDecimal("1.0"),
                new BigDecimal("10.0"),
                "ACTIVE"
        );

        given(strategyFeignClient.getStrategy(STRATEGY_ID)).willReturn(strategy);

        BacktestInternalResponse backtest = new BacktestInternalResponse(
                BACKTEST_ID,
                STRATEGY_ID,
                USER_ID,
                "005930",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                10_000_000L,
                "COMPLETED",
                new BigDecimal("12.5"),
                new BigDecimal("8.3"),
                new BigDecimal("60.0"),
                20,
                3
        );

        given(backtestFeignClient.getBacktest(BACKTEST_ID)).willReturn(backtest);

        mockMvc.perform(post("/api/v1/ai/analyses")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "strategyId": "%s",
                          "backtestId": "%s"
                        }
                        """.formatted(STRATEGY_ID, BACKTEST_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AI_RISK_0004"))
                .andExpect(jsonPath("$.message").value("해당 전략에 접근할 권한이 없습니다."));

        assertThat(aiRiskAnalysisCommandRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("다른 사용자의 백테스트로 AI 위험 분석을 요청하면 실패한다")
    void createAnalysis_backtestAccessDenied() throws Exception {
        // given
        UUID otherUserId = UUID.randomUUID();

        StrategyInternalResponse strategy = new StrategyInternalResponse(
                STRATEGY_ID,
                USER_ID,
                "005930",
                "삼성전자 전략",
                70000L,
                80000L,
                new BigDecimal("5.0"),
                new BigDecimal("10.0"),
                1_000_000L,
                100_000L,
                new BigDecimal("10.0"),
                new BigDecimal("1.0"),
                new BigDecimal("10.0"),
                "ACTIVE"
        );

        BacktestInternalResponse backtest = new BacktestInternalResponse(
                BACKTEST_ID,
                STRATEGY_ID,
                otherUserId,
                "005930",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                10_000_000L,
                "COMPLETED",
                new BigDecimal("12.5"),
                new BigDecimal("8.3"),
                new BigDecimal("60.0"),
                20,
                3
        );

        given(strategyFeignClient.getStrategy(STRATEGY_ID))
                .willReturn(strategy);

        given(backtestFeignClient.getBacktest(BACKTEST_ID))
                .willReturn(backtest);

        mockMvc.perform(post("/api/v1/ai/analyses")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "strategyId": "%s",
                              "backtestId": "%s"
                            }
                            """.formatted(STRATEGY_ID, BACKTEST_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AI_RISK_0005"))
                .andExpect(jsonPath("$.message").value("해당 백테스트에 접근할 권한이 없습니다."));

        assertThat(aiRiskAnalysisCommandRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("백테스트가 해당 전략의 결과가 아니면 AI 위험 분석 요청에 실패한다.")
    void createAnalysis_strategyBacktestMismatch() throws Exception {
        // given
        UUID otherStrategyId = UUID.randomUUID();

        StrategyInternalResponse strategy = new StrategyInternalResponse(
                STRATEGY_ID,
                USER_ID,
                "005930",
                "삼성전자 전략",
                70000L,
                80000L,
                new BigDecimal("5.0"),
                new BigDecimal("10.0"),
                1_000_000L,
                100_000L,
                new BigDecimal("10.0"),
                new BigDecimal("1.0"),
                new BigDecimal("10.0"),
                "ACTIVE"
        );

        BacktestInternalResponse backtest = new BacktestInternalResponse(
                BACKTEST_ID,
                otherStrategyId,
                USER_ID,
                "005930",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                10_000_000L,
                "COMPLETED",
                new BigDecimal("12.5"),
                new BigDecimal("8.3"),
                new BigDecimal("60.0"),
                20,
                3
        );

        given(strategyFeignClient.getStrategy(STRATEGY_ID))
                .willReturn(strategy);

        given(backtestFeignClient.getBacktest(BACKTEST_ID))
                .willReturn(backtest);

        // when & then
        mockMvc.perform(post("/api/v1/ai/analyses")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "strategyId": "%s",
                              "backtestId": "%s"
                            }
                            """.formatted(STRATEGY_ID, BACKTEST_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AI_RISK_0006"))
                .andExpect(jsonPath("$.message")
                        .value("백테스트가 해당 전략의 결과가 아닙니다."));

        assertThat(aiRiskAnalysisCommandRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("완료되지 않은 백테스트로 AI 위험 분석을 요청하면 실패한다.")
    void createAnalysis_backtestNotCompleted() throws Exception {
        StrategyInternalResponse strategy = new StrategyInternalResponse(
                STRATEGY_ID,
                USER_ID,
                "005930",
                "삼성전자 전략",
                70000L,
                80000L,
                new BigDecimal("5.0"),
                new BigDecimal("10.0"),
                1_000_000L,
                100_000L,
                new BigDecimal("10.0"),
                new BigDecimal("1.0"),
                new BigDecimal("10.0"),
                "ACTIVE"
        );

        BacktestInternalResponse backtest = new BacktestInternalResponse(
                BACKTEST_ID,
                STRATEGY_ID,
                USER_ID,
                "005930",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                10_000_000L,
                "RUNNING", // 핵심
                new BigDecimal("12.5"),
                new BigDecimal("8.3"),
                new BigDecimal("60.0"),
                20,
                3
        );

        given(strategyFeignClient.getStrategy(STRATEGY_ID))
                .willReturn(strategy);

        given(backtestFeignClient.getBacktest(BACKTEST_ID))
                .willReturn(backtest);

        mockMvc.perform(post("/api/v1/ai/analyses")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "strategyId": "%s",
                              "backtestId": "%s"
                            }
                            """.formatted(STRATEGY_ID, BACKTEST_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AI_RISK_0007"))
                .andExpect(jsonPath("$.message")
                        .value("완료되지 않은 백테스트는 AI 위험 분석을 요청할 수 없습니다."));

        assertThat(aiRiskAnalysisCommandRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("동일한 분석 요청이 PENDING 상태로 존재하면 기존 분석을 반환한다.")
    void createAnalysis_existingPending_returnsExistingAnalysis() throws Exception {
        StrategyInternalResponse strategy = new StrategyInternalResponse(
                STRATEGY_ID,
                USER_ID,
                "005930",
                "삼성전자 전략",
                70000L,
                80000L,
                new BigDecimal("5.0"),
                new BigDecimal("10.0"),
                1_000_000L,
                100_000L,
                new BigDecimal("10.0"),
                new BigDecimal("1.0"),
                new BigDecimal("10.0"),
                "ACTIVE"
        );

        BacktestInternalResponse backtest = new BacktestInternalResponse(
                BACKTEST_ID,
                STRATEGY_ID,
                USER_ID,
                "005930",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                10_000_000L,
                "COMPLETED",
                new BigDecimal("12.5"),
                new BigDecimal("8.3"),
                new BigDecimal("60.0"),
                20,
                3
        );

        given(strategyFeignClient.getStrategy(STRATEGY_ID))
                .willReturn(strategy);

        given(backtestFeignClient.getBacktest(BACKTEST_ID))
                .willReturn(backtest);

        String requestBody = """
            {
              "strategyId": "%s",
              "backtestId": "%s"
            }
            """.formatted(STRATEGY_ID, BACKTEST_ID);

        String firstResponse = mockMvc.perform(post("/api/v1/ai/analyses")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondResponse = mockMvc.perform(post("/api/v1/ai/analyses")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<AiRiskAnalysis> analyses = aiRiskAnalysisCommandRepository.findAll();

        assertThat(analyses).hasSize(1);

        JsonNode firstJson = objectMapper.readTree(firstResponse);
        JsonNode secondJson = objectMapper.readTree(secondResponse);

        String firstAnalysisId = firstJson.path("data").path("analysisId").asText();
        String secondAnalysisId = secondJson.path("data").path("analysisId").asText();

        assertThat(secondAnalysisId).isEqualTo(firstAnalysisId);
        assertThat(analyses).hasSize(1);
        assertThat(analyses.get(0).getId()).isEqualTo(UUID.fromString(firstAnalysisId));
    }
}
