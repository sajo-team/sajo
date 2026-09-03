package com.sajo.trading_service.ai_risk.service.processor;

import com.sajo.trading_service.ai_risk.event.AiRiskAnalysisRequestedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Tag("unit")
@Tag("ai-risk")
@ExtendWith(MockitoExtension.class)
class AiRiskAnalysisAsyncProcessorTest {

    @Mock
    private AiRiskAnalysisProcessor processor;

    @InjectMocks
    private AiRiskAnalysisAsyncProcessor asyncProcessor;

    @Test
    @DisplayName("비동기 AI 분석 작업을 실제 Processor에 위임한다")
    void process_delegatesToProcessor() {
        AiRiskAnalysisRequestedEvent event =
                mock(AiRiskAnalysisRequestedEvent.class);

        asyncProcessor.process(event);

        verify(processor).process(event);
    }
}