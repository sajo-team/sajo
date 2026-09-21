package com.sajo.market_service.support.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
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
public class SupportRagConfig {

    @Bean
    public VectorStore supportVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
