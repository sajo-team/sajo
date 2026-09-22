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
 */
public class SupportRagConfigurationConsistencyEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String RAG_ENABLED_PROPERTY = "sajo.support.rag.enabled";
    private static final String CHAT_MODEL_PROPERTY = "spring.ai.model.chat";
    private static final String EMBEDDING_MODEL_PROPERTY = "spring.ai.model.embedding";
    private static final String OPENAI = "openai";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean ragEnabled = Boolean.parseBoolean(environment.getProperty(RAG_ENABLED_PROPERTY, "false"));
        if (!ragEnabled) {
            return;
        }

        String chatModel = environment.getProperty(CHAT_MODEL_PROPERTY, "none");
        String embeddingModel = environment.getProperty(EMBEDDING_MODEL_PROPERTY, "none");
        if (OPENAI.equals(chatModel) && OPENAI.equals(embeddingModel)) {
            return;
        }

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
}
