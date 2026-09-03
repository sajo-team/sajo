package com.sajo.trading_service.ai_risk.service.processor;

import com.sajo.trading_service.ai_risk.event.AiRiskAnalysisRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiRiskAnalysisAsyncProcessor {

    private final AiRiskAnalysisProcessor processor;

    @Async("aiAnalysisExecutor")
    public void process(AiRiskAnalysisRequestedEvent event){
        processor.process(event);
    }
}
