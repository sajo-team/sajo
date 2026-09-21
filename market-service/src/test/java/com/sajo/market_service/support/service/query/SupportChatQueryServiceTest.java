package com.sajo.market_service.support.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.support.config.SupportRagProperties;
import com.sajo.market_service.support.controller.dto.response.SupportAskResponse;
import com.sajo.market_service.support.exception.SupportErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportChatQueryServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private VectorStore vectorStore;
    private ChatClient chatClient;
    private SupportChatQueryService service;

    @BeforeEach
    void setup() {
        vectorStore = mock(VectorStore.class);
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        SupportRagProperties properties =
                new SupportRagProperties("classpath:support-docs/market-service-overview.md", 3);
        service = new SupportChatQueryService(vectorStore, chatClient, properties);
    }

    @Test
    @DisplayName("벡터 저장소가 비어 있으면(문서 적재 실패) NO_RELEVANT_DOCUMENT_FOUND를 던진다")
    void throwsNoRelevantDocumentFoundWhenVectorStoreIsEmpty() {
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.ask(USER_ID, "현재가는 어떻게 조회하나요?"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SupportErrorCode.NO_RELEVANT_DOCUMENT_FOUND);
    }

    @Test
    @DisplayName("similaritySearch(임베딩 API 호출)가 실패하면 DOCUMENT_SEARCH_FAILED로 변환한다")
    void wrapsSimilaritySearchFailureAsDocumentSearchFailed() {
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                .thenThrow(new RuntimeException("OpenAI embedding API timeout"));

        assertThatThrownBy(() -> service.ask(USER_ID, "현재가는 어떻게 조회하나요?"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SupportErrorCode.DOCUMENT_SEARCH_FAILED);
    }

    @Test
    @DisplayName("문서 검색은 성공했지만 LLM 호출이 실패하면 LLM_RESPONSE_FAILED로 변환한다")
    void wrapsLlmFailureAsLlmResponseFailed() {
        Document document = new Document("본문", Map.of("documentTitle", "개요", "sectionTitle", "개요"));
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                .thenReturn(List.of(document));
        Consumer<ChatClient.PromptUserSpec> anyUserPrompt = any();
        when(chatClient.prompt().system(anyString()).user(anyUserPrompt).call().content())
                .thenThrow(new RuntimeException("OpenAI chat completion timeout"));

        assertThatThrownBy(() -> service.ask(USER_ID, "현재가는 어떻게 조회하나요?"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SupportErrorCode.LLM_RESPONSE_FAILED);
    }

    @Test
    @DisplayName("근거 문서 본문이 200자를 넘으면 소스 excerpt를 잘라서 반환한다")
    void truncatesSourceExcerptLongerThan200Characters() {
        String longBody = "가".repeat(250);
        Document document = new Document(longBody, Map.of("documentTitle", "개요", "sectionTitle", "1. 소개"));
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                .thenReturn(List.of(document));
        Consumer<ChatClient.PromptUserSpec> anyUserPrompt = any();
        when(chatClient.prompt().system(anyString()).user(anyUserPrompt).call().content())
                .thenReturn("답변입니다.");

        SupportAskResponse response = service.ask(USER_ID, "질문");

        assertThat(response.answer()).isEqualTo("답변입니다.");
        assertThat(response.sources()).hasSize(1);
        String excerpt = response.sources().get(0).excerpt();
        assertThat(excerpt).hasSize(203); // 200자 + "..."
        assertThat(excerpt).endsWith("...");
    }
}
