package com.sajo.trading_service.ai_risk.event;

import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import com.sajo.trading_service.ai_risk.service.command.AiRiskAnalysisResultService;
import com.sajo.trading_service.ai_risk.service.processor.AiRiskAnalysisAsyncProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiRiskAnalysisEventListener {

    private final AiRiskAnalysisAsyncProcessor  asyncProcessor;
    private final AiRiskAnalysisResultService resultService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AiRiskAnalysisRequestedEvent event){
        try{
            asyncProcessor.process(event);
        } catch(TaskRejectedException e){
            log.error(
                    "AI 분석 비동기 작업 제출 실패. analysisId={}",
                    event.analysisId(),
                    e
            );

            resultService.fail(
                    event.analysisId(),
                    AiAnalysisFailureType.INTERNAL_ERROR,
                    "AI 분석 작업 실행이 거부되었습니다."
            );
        }
    }
}
