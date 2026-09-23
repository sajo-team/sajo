package com.sajo.market_service.support.runner;

import com.sajo.market_service.support.config.SupportRagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

class SupportDocumentIngestionRunnerTest {

    private static final String SAMPLE_MARKDOWN = """
            # Market Service Overview

            이 문서는 market-service의 주요 API를 설명한다.

            ## 1. 현재가 조회

            현재가 조회는 KIS API를 통해 이루어진다.

            ## 2. 일별 시세

            일별 시세는 배치로 적재된다.
            """;

    @Test
    void logsAndSwallowsDocumentLoadFailureSoApplicationCanContinue() throws IOException {
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        Resource resource = mock(Resource.class);
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.getInputStream()).thenThrow(new IOException("document not found"));

        VectorStore vectorStore = mock(VectorStore.class);
        SupportRagProperties properties = new SupportRagProperties("classpath:support-docs/missing.md", 3, "gpt-4o-mini");
        SupportDocumentIngestionRunner runner =
                new SupportDocumentIngestionRunner(resourceLoader, vectorStore, properties);

        runner.run(null);

        verify(vectorStore, never()).add(any());
    }

    @Test
    void logsAndSwallowsUnexpectedRuntimeFailureSoApplicationCanContinue() {
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        when(resourceLoader.getResource(anyString())).thenThrow(new IllegalStateException("unexpected"));

        VectorStore vectorStore = mock(VectorStore.class);
        SupportRagProperties properties = new SupportRagProperties("classpath:support-docs/market-service-overview.md", 3, "gpt-4o-mini");
        SupportDocumentIngestionRunner runner =
                new SupportDocumentIngestionRunner(resourceLoader, vectorStore, properties);

        runner.run(null);

        verify(vectorStore, never()).add(any());
    }

    @Test
    void splitsMarkdownIntoOneChunkPerSectionWithTitleAndSectionMetadata() {
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        Resource resource = new ByteArrayResource(SAMPLE_MARKDOWN.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "market-service-overview.md";
            }
        };
        when(resourceLoader.getResource(anyString())).thenReturn(resource);

        VectorStore vectorStore = mock(VectorStore.class);
        SupportRagProperties properties = new SupportRagProperties("classpath:support-docs/market-service-overview.md", 3, "gpt-4o-mini");
        SupportDocumentIngestionRunner runner =
                new SupportDocumentIngestionRunner(resourceLoader, vectorStore, properties);

        runner.run(null);

        var captor = forClass(List.class);
        verify(vectorStore).add(captor.capture());
        @SuppressWarnings("unchecked")
        List<Document> chunks = (List<Document>) captor.getValue();

        // 첫 "##" 이전(제목 줄 + 개요 문단)이 "개요" 청크로, 이후 "##" 섹션 2개가 각각의
        // 청크로 나뉘어 총 3개의 청크가 만들어져야 한다.
        assertThat(chunks).hasSize(3);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.getMetadata()).containsEntry("documentTitle", "Market Service Overview"));

        assertThat(chunks.get(0).getMetadata()).containsEntry("sectionTitle", "개요");
        assertThat(chunks.get(0).getText()).contains("이 문서는 market-service의 주요 API를 설명한다");

        assertThat(chunks.get(1).getMetadata()).containsEntry("sectionTitle", "1. 현재가 조회");
        assertThat(chunks.get(1).getText()).contains("KIS API를 통해");

        assertThat(chunks.get(2).getMetadata()).containsEntry("sectionTitle", "2. 일별 시세");
        assertThat(chunks.get(2).getText()).contains("배치로 적재된다");
    }

    @Test
    void splitsNestedH3SubsectionsIntoSeparateChunksWithParentSectionTitle() {
        String markdownWithNestedSubsections = """
                # Market Service Overview

                ## 5. 지금까지 구현된 조회 API

                ### 현재가 조회

                현재가 조회는 KIS API를 통해 이루어진다.

                ### 일별 시세

                일별 시세는 배치로 적재된다.

                ## 6. 다음 섹션

                다음 섹션 본문.
                """;

        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        Resource resource = new ByteArrayResource(markdownWithNestedSubsections.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "market-service-overview.md";
            }
        };
        when(resourceLoader.getResource(anyString())).thenReturn(resource);

        VectorStore vectorStore = mock(VectorStore.class);
        SupportRagProperties properties = new SupportRagProperties("classpath:support-docs/market-service-overview.md", 3, "gpt-4o-mini");
        SupportDocumentIngestionRunner runner =
                new SupportDocumentIngestionRunner(resourceLoader, vectorStore, properties);

        runner.run(null);

        var captor = forClass(List.class);
        verify(vectorStore).add(captor.capture());
        @SuppressWarnings("unchecked")
        List<Document> chunks = (List<Document>) captor.getValue();

        // 첫 "##" 앞의 제목 줄("# Market Service Overview")이 "개요" 청크 하나를 만들고,
        // "##" 섹션 하나에 딸린 "###" 하위 섹션 2개가 각자 별도 청크로 나뉘며(그 사이에 낀
        // "##" 자체의 본문은 비어 있어 addChunkIfNotBlank에서 스킵된다), 그 뒤 "##" 섹션은
        // 그대로 청크 하나가 되어 총 4개다.
        assertThat(chunks).hasSize(4);

        assertThat(chunks.get(0).getMetadata()).containsEntry("sectionTitle", "개요");
        assertThat(chunks.get(0).getText()).contains("Market Service Overview");

        assertThat(chunks.get(1).getMetadata())
                .containsEntry("sectionTitle", "5. 지금까지 구현된 조회 API > 현재가 조회");
        assertThat(chunks.get(1).getText()).contains("KIS API를 통해");

        assertThat(chunks.get(2).getMetadata())
                .containsEntry("sectionTitle", "5. 지금까지 구현된 조회 API > 일별 시세");
        assertThat(chunks.get(2).getText()).contains("배치로 적재된다");

        assertThat(chunks.get(3).getMetadata()).containsEntry("sectionTitle", "6. 다음 섹션");
        assertThat(chunks.get(3).getText()).contains("다음 섹션 본문");
    }
}
