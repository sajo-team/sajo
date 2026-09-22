package com.sajo.market_service.support.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * {@code sajo.support.rag.enabled}(RAG 챗봇 자체 on/off)와
 * {@code spring.ai.model.chat}/{@code spring.ai.model.embedding}(Spring AI의 OpenAI 자동구성
 * on/off)은 서로 다른 프로퍼티라서 독립적으로 설정할 수 있다. 그런데 {@code SupportRagConfig}는
 * RAG 플래그가 켜지면 {@code EmbeddingModel}/{@code ChatClient.Builder} 빈을 필요로 하고, 이
 * 빈들은 오직 {@code spring.ai.model.chat}/{@code embedding}이 "openai"일 때만 Spring AI가
 * 만들어준다. 즉 운영자가 {@code SUPPORT_RAG_ENABLED=true}만 켜고 {@code SPRING_AI_CHAT_MODEL}/
 * {@code SPRING_AI_EMBEDDING_MODEL}을 같이 openai로 맞추는 걸 잊으면, 빈이 없어
 * market-service 전체가 기동 실패한다(코드 리뷰로 발견 — 이 PR이 애초에 없애려던 SPOF가
 * 다른 형태로 재발할 수 있는 경로).
 *
 * <p>두 플래그를 하나로 강제 파생시키는 대신(다른 스케줄러/웹소켓 플래그들과 마찬가지로
 * 환경변수로 독립 제어하는 프로젝트 컨벤션을 유지하기 위해), 이 검증기는 애플리케이션 컨텍스트가
 * 만들어지기 전(환경 준비 단계)에 이 불일치를 감지해 어떤 값을 맞춰야 하는지 알려주는 명확한
 * 예외로 즉시 실패시킨다. 이렇게 하면 실패하더라도 최소한 원인을 바로 알 수 있는 형태로
 * 실패한다({@code NoSuchBeanDefinitionException} 같은 간접적인 스택 트레이스를 운영자가 직접
 * 해석해야 하는 상황을 피한다).</p>
 *
 * <p><b>{@code spring.ai.openai.api-key}도 함께 검증한다.</b> 처음에는 chat/embedding 셀렉터가
 * 둘 다 "openai"인지만 확인했는데, 코드 리뷰로 지적됐듯 그것만으로는 부족하다 — 두 셀렉터가
 * "openai"이기만 하면 Spring AI 자동구성은 {@code OpenAiChatModel}/{@code OpenAiEmbeddingModel}
 * 빈을 즉시 생성하고, 그 생성 과정에서 API 키가 비어 있으면 즉시 예외를 던진다(이 프로젝트의
 * contextLoads() 디버깅 이력에서 실제로 겪은 문제와 동일한 성격). 즉 운영자가 이 검증기가
 * 안내하는 대로 SPRING_AI_CHAT_MODEL/EMBEDDING_MODEL만 맞추고 OPENAI_API_KEY를 채우는 걸
 * 잊으면, 이 검증기를 통과한 바로 다음 단계에서 이 PR이 없애려던 것과 같은 종류의 SPOF가
 * 재발한다. 그래서 chat/embedding이 둘 다 "openai"인 경우 API 키도 비어 있지 않은지 반드시
 * 함께 검증한다.</p>
 */
public class SupportRagConfigurationConsistencyEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String RAG_ENABLED_PROPERTY = "sajo.support.rag.enabled";
    private static final String CHAT_MODEL_PROPERTY = "spring.ai.model.chat";
    private static final String EMBEDDING_MODEL_PROPERTY = "spring.ai.model.embedding";
    private static final String OPENAI_API_KEY_PROPERTY = "spring.ai.openai.api-key";
    private static final String OPENAI = "openai";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean ragEnabled = Boolean.parseBoolean(environment.getProperty(RAG_ENABLED_PROPERTY, "false"));
        if (!ragEnabled) {
            return;
        }

        String chatModel = environment.getProperty(CHAT_MODEL_PROPERTY, "none");
        String embeddingModel = environment.getProperty(EMBEDDING_MODEL_PROPERTY, "none");
        if (!OPENAI.equals(chatModel) || !OPENAI.equals(embeddingModel)) {
            throw new IllegalStateException(
                    RAG_ENABLED_PROPERTY + "=true(SUPPORT_RAG_ENABLED)로 RAG 기반 고객 응대 챗봇을 켰지만 "
                            + CHAT_MODEL_PROPERTY + "=\"" + chatModel + "\", " + EMBEDDING_MODEL_PROPERTY + "=\""
                            + embeddingModel + "\"입니다(둘 다 \"" + OPENAI + "\"여야 합니다). 이 상태로 기동하면 "
                            + "SupportRagConfig가 필요로 하는 EmbeddingModel/ChatClient.Builder 빈이 없어 "
                            + "market-service 전체 기동이 실패합니다. SPRING_AI_CHAT_MODEL=" + OPENAI + ", "
                            + "SPRING_AI_EMBEDDING_MODEL=" + OPENAI + "를 함께 설정하거나, RAG 챗봇을 쓰지 않으려면 "
                            + "SUPPORT_RAG_ENABLED=false로 되돌리세요."
            );
        }

        String apiKey = environment.getProperty(OPENAI_API_KEY_PROPERTY, "");
        if (apiKey.isBlank()) {
            throw new IllegalStateException(
                    RAG_ENABLED_PROPERTY + "=true(SUPPORT_RAG_ENABLED)이고 " + CHAT_MODEL_PROPERTY + "/"
                            + EMBEDDING_MODEL_PROPERTY + "도 \"" + OPENAI + "\"로 맞춰져 있지만 "
                            + OPENAI_API_KEY_PROPERTY + "(OPENAI_API_KEY)가 비어 있습니다. 이 상태로 기동하면 "
                            + "Spring AI가 OpenAiChatModel/OpenAiEmbeddingModel 빈을 생성하는 과정에서 API 키 "
                            + "검증에 실패해 market-service 전체가 기동 실패합니다. OPENAI_API_KEY를 채우거나, "
                            + "RAG 챗봇을 쓰지 않으려면 SUPPORT_RAG_ENABLED=false로 되돌리세요."
            );
        }
    }
}
