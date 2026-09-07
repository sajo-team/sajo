package com.sajo.trading_service.ai_risk.controller.dto.response;

import com.sajo.trading_service.ai_risk.document.AiAnalysisHistory;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AiAnalysisAuditDetailResponse(
        UUID analysisId,
        UUID userId,
        UUID strategyId,
        UUID backtestId,
        Map<String, Object> requestSnapshot,
        PromptResponse prompt,
        LlmResponse response,
        ValidationResponse validation,
        MetadataResponse metadata,
        Result result,
        Instant createdAt
) {

    public static AiAnalysisAuditDetailResponse from(AiAnalysisHistory history){
        return new AiAnalysisAuditDetailResponse(
                history.getAnalysisId(),
                history.getUserId(),
                history.getStrategyId(),
                history.getBacktestId(),
                history.getRequestSnapshot(),
                PromptResponse.from(history.getPrompt()),
                LlmResponse.from(history.getResponse()),
                ValidationResponse.from(history.getValidation()),
                MetadataResponse.from(history.getMetadata()),
                Result.from(history.getResult()),
                history.getCreatedAt()
        );
    }

    public record PromptResponse(
            String version,
            String content
    ) {
        private static PromptResponse from(AiAnalysisHistory.PromptSnapshot prompt){

            if(prompt == null){
                return null;
            }
            return new PromptResponse(
                    prompt.version(),
                    prompt.content()
            );
        }
    }

    public record LlmResponse(
            String rawResponse
    ) {
        private static LlmResponse from(AiAnalysisHistory.ResponseSnapshot response) {

            if(response == null){
                return null;
            }

            return new LlmResponse(response.rawResponse());
        }
    }

    public record ValidationResponse(
            Boolean structureValid,
            Boolean contentValid,
            List<String> errors
    ) {
        private static ValidationResponse from(
                AiAnalysisHistory.ValidationSnapshot validation
        ) {

            if(validation == null){
                return null;
            }

            return new ValidationResponse(
                    validation.structureValid(),
                    validation.contentValid(),
                    validation.errors()
            );
        }
    }

    public record MetadataResponse(
            String model,
            Long latencyMs
    ) {
        private static MetadataResponse from(
                AiAnalysisHistory.MetadataSnapshot metadata
        ) {

            if(metadata == null){
                return null;
            }

            return new MetadataResponse(
                    metadata.model(),
                    metadata.latencyMs()
            );
        }
    }

    public record Result(
            AiAnalysisStatus status,
            AiAnalysisFailureType failureType
    ){
        private static Result from(
                AiAnalysisHistory.ResultSnapshot result
        ){
            if(result == null){
                return null;
            }

            return new Result(
                    result.status(),
                    result.failureType()
            );
        }
    }
}
