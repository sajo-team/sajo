package com.sajo.trading_service.ai_risk.controller.dto.response;

import com.sajo.trading_service.ai_risk.domain.AiPromptKey;
import com.sajo.trading_service.ai_risk.domain.AiPromptStatus;
import com.sajo.trading_service.ai_risk.domain.AiPromptVersion;

import java.time.Instant;
import java.util.UUID;

public record AiPromptVersionCreateResponse(
        UUID id,
        AiPromptKey promptKey,
        String version,
        AiPromptStatus status,
        Instant deployedAt
) {
    public static AiPromptVersionCreateResponse from(AiPromptVersion promptVersion){
        return new AiPromptVersionCreateResponse(
                promptVersion.getId(),
                promptVersion.getPromptKey(),
                promptVersion.getVersion(),
                promptVersion.getStatus(),
                promptVersion.getDeployedAt()
        );
    }
}
