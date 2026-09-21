package com.sajo.market_service.support.runner;

import com.sajo.market_service.support.config.SupportRagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;

import static org.mockito.Mockito.*;

class SupportDocumentIngestionRunnerTest {

    @Test
    void logsAndSwallowsDocumentLoadFailureSoApplicationCanContinue() throws IOException {
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        Resource resource = mock(Resource.class);
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.getInputStream()).thenThrow(new IOException("document not found"));

        VectorStore vectorStore = mock(VectorStore.class);
        SupportRagProperties properties = new SupportRagProperties("classpath:support-docs/missing.md", 3);
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
        SupportRagProperties properties = new SupportRagProperties("classpath:support-docs/market-service-overview.md", 3);
        SupportDocumentIngestionRunner runner =
                new SupportDocumentIngestionRunner(resourceLoader, vectorStore, properties);

        runner.run(null);

        verify(vectorStore, never()).add(any());
    }
}
