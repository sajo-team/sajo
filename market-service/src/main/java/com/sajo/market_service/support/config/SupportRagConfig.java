package com.sajo.market_service.support.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 기반 고객 응대 챗봇(도전 과제)이 사용하는 벡터 저장소를 구성한다.
 *
 * <p>MVP 범위(문서 1종, 십여 개 청크)에서는 별도 벡터 DB 인프라(pgvector 등)를 새로 구축하는
 * 대신 Spring AI가 제공하는 인메모리 {@link SimpleVectorStore}로 충분하다고 판단했다. 애플리케이션
 * 재기동 시 {@code SupportDocumentIngestionRunner}가 매번 문서를 다시 임베딩해 채우므로 영속성이
 * 없어도 문제되지 않는다. 문서 종류/양이 늘어나 재임베딩 비용이 커지면 그때 pgvector 등 영속
 * 벡터 저장소로 교체하면 된다.
 */
@Configuration
@EnableConfigurationProperties(SupportRagProperties.class)
@ConditionalOnProperty(prefix = "sajo.support.rag", name = "enabled", havingValue = "true")
public class SupportRagConfig {

    @Bean
    public VectorStore supportVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    // Spring AI 자동구성은 ChatClient.Builder만 제공하고, 실제 사용할 ChatClient 빈은
    // 애플리케이션에서 직접 만들어야 한다(여러 개의 서로 다른 설정을 가진 ChatClient가
    // 있을 수 있기 때문). SupportChatService가 ChatClient를 그대로 주입받아 쓰므로 여기서
    // 기본 설정 그대로 build()한 빈을 하나 등록한다.
    //
    // 모델을 gpt-4o-mini로 명시 고정한다. 모델을 지정하지 않으면 spring-ai-openai(2.0.1)의
    // 기본값이 그대로 쓰이는데, Zipkin 트레이스로 실측해보니 그 기본값이 추론(reasoning)
    // 모델인 gpt-5-mini였다. 이 챗봇은 사전에 적재된 FAQ 문서에서 관련 내용을 찾아 그대로
    // 요약/인용하는 수준의 단순 응답이라 reasoning이 필요 없는데도, 매 요청마다 내부
    // 추론 단계를 거치느라 챗 완성 호출 하나에만 6초 이상이 걸리고 있었다(임베딩 호출은
    // 350ms 수준으로 병목이 아니었다). gpt-4o-mini는 reasoning을 하지 않는 일반 모델이라
    // 같은 작업에 훨씬 빠르게 응답한다.
    @Bean
    public ChatClient supportChatClient(ChatClient.Builder chatClientBuilder, SupportRagProperties properties) {
        return chatClientBuilder
                .defaultOptions(OpenAiChatOptions.builder().model(properties.chatModelName()))
                .build();
    }
}
