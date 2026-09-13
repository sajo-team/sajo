package com.sajo.market_service.market.dto.kis;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisApprovalKeyResponse(
        @JsonProperty("approval_key") String approvalKey
) {

    public boolean isSuccess() {
        return approvalKey != null && !approvalKey.isBlank();
    }
}
