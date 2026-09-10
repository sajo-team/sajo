package com.sajo.market_service.market.dto.kis;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record KisFinancialRatioResponse(
        @JsonProperty("rt_cd") String resultCode,
        @JsonProperty("msg_cd") String messageCode,
        @JsonProperty("msg1") String message,
        List<KisFinancialRatioOutput> output
) {
    public boolean isSuccessful() {
        return "0".equals(resultCode);
    }

    public record KisFinancialRatioOutput(
            @JsonProperty("stac_yymm") String financialReferenceYearMonth,
            @JsonProperty("roe_val") String roe
    ) { }
}
