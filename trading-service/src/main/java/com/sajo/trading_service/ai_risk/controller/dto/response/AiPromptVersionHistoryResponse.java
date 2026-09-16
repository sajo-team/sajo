package com.sajo.trading_service.ai_risk.controller.dto.response;

import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import com.sajo.trading_service.ai_risk.domain.AiPromptKey;
import com.sajo.trading_service.ai_risk.domain.AiPromptStatus;
import com.sajo.trading_service.ai_risk.domain.AiPromptVersion;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AiPromptVersionHistoryResponse(
        UUID id,
        AiPromptKey promptKey,
        String version,
        AiPromptStatus status,
        Instant deployedAt,
        Instant retiredAt,
        long totalCount,
        long failedCount,
        double failureRate,
        Map<AiAnalysisFailureType, Long> failureTypeCounts
) {

    public static AiPromptVersionHistoryResponse of(
            AiPromptVersion promptVersion,
            long totalCount,
            long failedCount,
            Map<AiAnalysisFailureType, Long> failureTypeCounts
    ) {
        double failureRate = totalCount == 0 ? 0.0 : (double) failedCount / totalCount * 100;

        return new AiPromptVersionHistoryResponse(
                promptVersion.getId(),
                promptVersion.getPromptKey(),
                promptVersion.getVersion(),
                promptVersion.getStatus(),
                promptVersion.getDeployedAt(),
                promptVersion.getRetiredAt(),
                totalCount,
                failedCount,
                failureRate,
                failureTypeCounts
        );
    }
}
