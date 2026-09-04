package com.sajo.trading_service.ai_risk.controller.dto.request;

import com.sajo.trading_service.ai_risk.domain.AiPromptKey;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AiPromptVersionCreateRequest(

        @NotNull
        AiPromptKey promptKey,

        @NotBlank
        String promptContent,

        String changeSummary
) {
}
