package com.sajo.market_service.support.runner;

import com.sajo.market_service.support.config.SupportRagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 애플리케이션 기동 시 RAG 기반 고객 응대 챗봇(도전 과제)이 참조할 도메인 문서를 읽어 청크로
 * 나누고 벡터 저장소에 적재한다.
 *
 * <p><b>청킹 전략: 마크다운 {@code ##} 섹션 단위 분할.</b> 고정 길이(토큰 수) 분할 대신 문서의
 * 논리적 구획(섹션)을 그대로 청크 경계로 삼았다. 이 문서는 이미 "현재가 조회", "일별 시세" 등
 * 주제별로 {@code ##} 헤딩이 나뉘어 있어서, 섹션 단위로 자르면 (1) 한 청크 안에 서로 다른 주제가
 * 섞여 검색 정확도가 떨어지는 것을 막을 수 있고, (2) 답변에 "몇 번째 섹션을 참고했는지" 그대로
 * 출처로 노출할 수 있어 고정 길이 분할보다 출처 표기가 자연스럽다. 다만 섹션 하나가 지나치게 길면
 * (예: 코드 예시가 긴 섹션) 청크가 커져 임베딩 품질이 떨어질 수 있는데, 현재 문서 규모(섹션당
 * 수십 줄 이내)에서는 문제되지 않아 별도 하위 분할은 적용하지 않았다. 문서가 더 커지면 섹션 내
 * 재귀 분할을 추가로 검토해야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "sajo.support.rag", name = "enabled", havingValue = "true")
public class SupportDocumentIngestionRunner implements ApplicationRunner {

    private static final Pattern SECTION_HEADING_PATTERN = Pattern.compile("(?m)^##\\s+(.+)$");

    private final ResourceLoader resourceLoader;
    private final VectorStore vectorStore;
    private final SupportRagProperties properties;

    /**
     * RAG는 도전 과제 성격의 부가 기능이라, 문서 로딩/임베딩 실패가 market-service 전체의
     * 기동 실패로 이어져서는 안 된다({@link ApplicationRunner#run}에서 예외가 전파되면 Spring
     * 컨텍스트 초기화 자체가 중단된다). 그래서 여기서는 예외를 삼키고 로그만 남긴다 — 그 결과
     * 벡터 저장소가 빈 상태로 남으면 {@code SupportChatService}가 매 질문마다
     * {@code NO_RELEVANT_DOCUMENT_FOUND}를 반환하게 되지만, 이는 애플리케이션 전체 다운보다는
     * 훨씬 낫다.
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            Resource resource = resourceLoader.getResource(properties.documentPath());
            String markdown;
            try (InputStream inputStream = resource.getInputStream()) {
                markdown = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            }

            String documentTitle = extractDocumentTitle(markdown, resource.getFilename());
            List<Document> chunks = splitIntoSectionChunks(markdown, documentTitle);

            vectorStore.add(chunks);
            log.info("RAG 도메인 문서 적재 완료. documentTitle={}, chunkCount={}", documentTitle, chunks.size());
        } catch (IOException | RuntimeException exception) {
            log.error("RAG 도메인 문서 적재에 실패했습니다. 벡터 저장소가 비어 있는 상태로 기동을 계속합니다. "
                    + "documentPath={}", properties.documentPath(), exception);
        }
    }

    private String extractDocumentTitle(String markdown, String fallbackFilename) {
        Matcher titleMatcher = Pattern.compile("(?m)^#\\s+(.+)$").matcher(markdown);
        if (titleMatcher.find()) {
            return titleMatcher.group(1).trim();
        }
        return fallbackFilename != null ? fallbackFilename : "Untitled Document";
    }

    private List<Document> splitIntoSectionChunks(String markdown, String documentTitle) {
        List<Document> chunks = new ArrayList<>();
        Matcher headingMatcher = SECTION_HEADING_PATTERN.matcher(markdown);

        int previousSectionEnd = 0;
        String previousSectionTitle = "개요";
        while (headingMatcher.find()) {
            addChunkIfNotBlank(chunks, documentTitle, previousSectionTitle,
                    markdown.substring(previousSectionEnd, headingMatcher.start()));
            previousSectionTitle = headingMatcher.group(1).trim();
            previousSectionEnd = headingMatcher.end();
        }
        addChunkIfNotBlank(chunks, documentTitle, previousSectionTitle,
                markdown.substring(previousSectionEnd));

        return chunks;
    }

    private void addChunkIfNotBlank(
            List<Document> chunks, String documentTitle, String sectionTitle, String sectionBody) {
        String trimmedBody = sectionBody.strip();
        if (trimmedBody.isEmpty()) {
            return;
        }
        Map<String, Object> metadata = Map.of(
                "documentTitle", documentTitle,
                "sectionTitle", sectionTitle
        );
        chunks.add(new Document("## " + sectionTitle + "\n" + trimmedBody, metadata));
    }
}
