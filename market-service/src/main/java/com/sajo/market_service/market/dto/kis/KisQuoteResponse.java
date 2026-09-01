package com.sajo.market_service.market.dto.kis;

import com.fasterxml.jackson.annotation.JsonProperty;

/** KIS inquire-price API의 원본 응답 중 Market에서 사용하는 필드다. */
public record KisQuoteResponse(
        @JsonProperty("rt_cd") String resultCode,
        @JsonProperty("msg_cd") String messageCode,
        @JsonProperty("msg1") String message,
        KisQuoteOutput output
) {

    public boolean isSuccess() {
        return "0".equals(resultCode);
    }

    public record KisQuoteOutput(
            @JsonProperty("stck_prpr") String currentPrice,
            @JsonProperty("stck_oprc") String openPrice,
            @JsonProperty("stck_hgpr") String highPrice,
            @JsonProperty("stck_lwpr") String lowPrice,
            @JsonProperty("stck_sdpr") String previousClosePrice,
            @JsonProperty("prdy_vrss") String changePrice,
            @JsonProperty("prdy_ctrt") String changeRate,
            @JsonProperty("acml_vol") String accumulatedVolume,
            @JsonProperty("acml_tr_pbmn") String tradeAmount,
            @JsonProperty("hts_avls") String marketCapitalization,
            String per,
            String pbr,
            String eps,
            String bps
    ) {
    }
}
