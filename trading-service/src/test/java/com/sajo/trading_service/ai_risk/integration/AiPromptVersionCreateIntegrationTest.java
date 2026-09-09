package com.sajo.trading_service.ai_risk.integration;

import com.sajo.trading_service.ai_risk.domain.AiPromptKey;
import com.sajo.trading_service.ai_risk.domain.AiPromptStatus;
import com.sajo.trading_service.ai_risk.domain.AiPromptVersion;
import com.sajo.trading_service.ai_risk.repository.command.AiPromptVersionCommandRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

@Tag("integration")
@Tag("ai-risk")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
public class AiPromptVersionCreateIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add(
                "spring.jpa.hibernate.ddl-auto",
                () -> "create-drop"
        );
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiPromptVersionCommandRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("관리자는 최초 AI 프롬프트 버전을 등록할 수 있다")
    void createPromptVersion_success() throws Exception {

        String request = """
            {
              "promptKey": "RISK_ANALYSIS",
              "promptContent": "AI 위험 분석 프롬프트입니다.",
              "changeSummary": "최초 프롬프트 등록"
            }
            """;

        mockMvc.perform(
                        post("/api/v1/admin/ai/prompt-versions")
                                .header("X-User-Role", "ADMIN")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.promptKey")
                        .value("RISK_ANALYSIS"))
                .andExpect(jsonPath("$.data.version")
                        .value("v1"))
                .andExpect(jsonPath("$.data.status")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.data.deployedAt")
                        .exists());

        List<AiPromptVersion> prompts = repository.findAll();

        assertThat(prompts).hasSize(1);

        AiPromptVersion saved = prompts.getFirst();

        assertThat(saved.getPromptKey())
                .isEqualTo(AiPromptKey.RISK_ANALYSIS);
        assertThat(saved.getVersion())
                .isEqualTo("v1");
        assertThat(saved.getStatus())
                .isEqualTo(AiPromptStatus.ACTIVE);
        assertThat(saved.getPromptContent())
                .isEqualTo("AI 위험 분석 프롬프트입니다.");
        assertThat(saved.getChangeSummary())
                .isEqualTo("최초 프롬프트 등록");
    }

    @Test
    @DisplayName("새 프롬프트를 등록하면 기존 ACTIVE 버전은 비활성화되고 v2가 ACTIVE로 등록된다")
    void createPromptVersion_shouldRetirePreviousVersion() throws Exception {

        String firstRequest = """
            {
              "promptKey": "RISK_ANALYSIS",
              "promptContent": "기존 프롬프트",
              "changeSummary": "최초 등록"
            }
            """;

        mockMvc.perform(
                        post("/api/v1/admin/ai/prompt-versions")
                                .header("X-User-Role", "ADMIN")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(firstRequest)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.version").value("v1"));

        String secondRequest = """
            {
              "promptKey": "RISK_ANALYSIS",
              "promptContent": "개선된 프롬프트",
              "changeSummary": "위험 분석 정확도 개선"
            }
            """;

        mockMvc.perform(
                        post("/api/v1/admin/ai/prompt-versions")
                                .header("X-User-Role", "ADMIN")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(secondRequest)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.promptKey")
                        .value("RISK_ANALYSIS"))
                .andExpect(jsonPath("$.data.version")
                        .value("v2"))
                .andExpect(jsonPath("$.data.status")
                        .value("ACTIVE"));

        List<AiPromptVersion> prompts = repository.findAll();

        assertThat(prompts).hasSize(2);

        AiPromptVersion v1 = prompts.stream()
                .filter(prompt -> prompt.getVersion().equals("v1"))
                .findFirst()
                .orElseThrow();

        AiPromptVersion v2 = prompts.stream()
                .filter(prompt -> prompt.getVersion().equals("v2"))
                .findFirst()
                .orElseThrow();

        assertThat(v1.getStatus())
                .isEqualTo(AiPromptStatus.RETIRED);
        assertThat(v1.getRetiredAt())
                .isNotNull();

        assertThat(v2.getStatus())
                .isEqualTo(AiPromptStatus.ACTIVE);
        assertThat(v2.getRetiredAt())
                .isNull();
        assertThat(v2.getPromptContent())
                .isEqualTo("개선된 프롬프트");
    }

    @Test
    @DisplayName("관리자가 아닌 사용자가 프롬프트 버전을 등록하면 403을 반환한다")
    void createPromptVersion_forbidden() throws Exception {

        String request = """
            {
              "promptKey": "RISK_ANALYSIS",
              "promptContent": "AI 위험 분석 프롬프트입니다.",
              "changeSummary": "프롬프트 변경"
            }
            """;

        mockMvc.perform(
                        post("/api/v1/admin/ai/prompt-versions")
                                .header("X-User-Role", "USER")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isForbidden());

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("프롬프트 내용이 공백이면 프롬프트 버전 등록에 실패한다")
    void createPromptVersion_blankPromptContent() throws Exception {

        String request = """
            {
              "promptKey": "RISK_ANALYSIS",
              "promptContent": "   ",
              "changeSummary": "프롬프트 변경"
            }
            """;

        mockMvc.perform(
                        post("/api/v1/admin/ai/prompt-versions")
                                .header("X-User-Role", "ADMIN")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest());

        assertThat(repository.findAll()).isEmpty();
    }
}
