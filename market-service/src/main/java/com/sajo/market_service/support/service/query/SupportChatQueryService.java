package com.sajo.market_service.support.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.support.config.SupportRagProperties;
import com.sajo.market_service.support.controller.dto.response.SupportAskResponse;
import com.sajo.market_service.support.controller.dto.response.SupportSourceReference;
import com.sajo.market_service.support.exception.SupportErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "sajo.support.rag", name = "enabled", havingValue = "true")
public class SupportChatQueryService {

    private static final int SOURCE_EXCERPT_MAX_LENGTH = 200;

    /**
     * 메트릭/코드 수준의 확정 답변을 요구하는 다른 서비스(AlertAnalyzer 등)와 달리, 이 챗봇은
     * 사용자에게 직접 노출되는 응답이라 "모르면 모른다고 답하라"를 최우선 지침으로 둔다.
     * 검증 과정(5절)에서 실제로 문서에 없는 내용을 그럴듯하게 지어내는 hallucination을 관찰한 뒤,
     * "제공된 컨텍스트 안의 정보만 사용하라"는 문장을 추가해 개선했다.
     */
    private static final String SYSTEM_PROMPT = """
            너는 SAJO 서비스의 고객 응대 챗봇이다.
            아래 [참고 문서]에 있는 내용만 근거로 답변하라.
            [참고 문서]에 질문에 대한 답이 없으면, 지어내지 말고 "문서에서 관련 내용을 찾지 못했습니다"라고 답하라.
            답변은 한국어로, 간결하게 작성하라.
            """;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final SupportRagProperties properties;

    public SupportAskResponse ask(UUID userId, String question) {
        // 호출마다 OpenAI 비용이 발생하는 API라, 누가 얼마나 호출했는지 추적할 수 있도록
        // userId를 감사 로그로 남긴다(질문 원문은 개인정보/민감 정보가 섞일 수 있어 로그에는
        // 남기지 않는다).
        log.info("RAG 챗봇 질의 요청. userId={}", userId);

        List<Document> relevantDocuments = searchRelevantDocuments(question);

        if (relevantDocuments.isEmpty()) {
            // similarityThreshold를 두지 않아 벡터 저장소에 청크가 하나라도 있으면 항상
            // topK개가 반환된다. 즉 이 분기는 "질문과 무관함"이 아니라 SupportDocumentIngestionRunner의
            // 문서 적재 실패(벡터 저장소가 비어 있음)를 의미한다. 자세한 설명은 SupportErrorCode 참고.
            throw new BusinessException(SupportErrorCode.NO_RELEVANT_DOCUMENT_FOUND);
        }

        String context = buildContext(relevantDocuments);
        String answer = generateAnswer(question, context);
        List<SupportSourceReference> sources = toSourceReferences(relevantDocuments);

        return new SupportAskResponse(answer, sources);
    }

    private List<Document> searchRelevantDocuments(String question) {
        try {
            return vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(question)
                            .topK(properties.topK())
                            .build()
            );
        } catch (RuntimeException exception) {
            // similaritySearch()는 내부적으로 임베딩 API(OpenAI)를 호출한다. LLM 호출 실패와
            // 동일한 성격의 외부 AI 호출 실패이므로, 여기서도 잡아서 도메인 예외로 변환해야
            // GlobalExceptionHandler의 일반 500(INTERNAL_SERVER_ERROR)으로 새어나가지 않는다.
            log.warn("관련 문서 검색에 실패했습니다. question={}", question, exception);
            throw new BusinessException(SupportErrorCode.DOCUMENT_SEARCH_FAILED);
        }
    }

    private String buildContext(List<Document> documents) {
        StringBuilder builder = new StringBuilder();
        for (Document document : documents) {
            builder.append(document.getText()).append("\n\n");
        }
        return builder.toString();
    }

    private String generateAnswer(String question, String context) {
        try {
            return chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt -> userPrompt.text("""
                            [참고 문서]
                            %s

                            [질문]
                            %s
                            """.formatted(context, question)))
                    .call()
                    .content();
        } catch (RuntimeException exception) {
            log.warn("LLM 응답 생성에 실패했습니다. question={}", question, exception);
            throw new BusinessException(SupportErrorCode.LLM_RESPONSE_FAILED);
        }
    }

    private List<SupportSourceReference> toSourceReferences(List<Document> documents) {
        return documents.stream()
                .map(document -> new SupportSourceReference(
                        String.valueOf(document.getMetadata().getOrDefault("documentTitle", "Unknown")),
                        String.valueOf(document.getMetadata().getOrDefault("sectionTitle", "Unknown")),
                        truncate(document.getText())
                ))
                .toList();
    }

    private String truncate(String text) {
        if (text == null || text.length() <= SOURCE_EXCERPT_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, SOURCE_EXCERPT_MAX_LENGTH) + "...";
    }
}
