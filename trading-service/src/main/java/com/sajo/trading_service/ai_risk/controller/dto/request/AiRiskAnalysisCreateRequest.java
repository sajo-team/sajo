package com.sajo.trading_service.ai_risk.controller.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AiRiskAnalysisCreateRequest(

        @NotNull(message = "전략 ID는 필수입니다.")
        UUID strategyId,

        @NotNull(message = "백테스트 ID는 필수입니다.")
        UUID backtestId
) {
}
