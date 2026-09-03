package com.sajo.market_service.market.dto.kis;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record KisDailyPriceResponse(
        @JsonProperty("rt_cd") String resultCode,
        @JsonProperty("msg_cd") String messageCode,
        @JsonProperty("msg1") String message,
        List<KisDailyPriceOutput> output2
) {
    public boolean isSuccess() { return "0".equals(resultCode); }

    public record KisDailyPriceOutput(
            @JsonProperty("stck_bsop_date") String tradeDate,
            @JsonProperty("stck_oprc") String openPrice,
            @JsonProperty("stck_hgpr") String highPrice,
            @JsonProperty("stck_lwpr") String lowPrice,
            @JsonProperty("stck_clpr") String closePrice,
            @JsonProperty("acml_vol") String volume,
            @JsonProperty("acml_tr_pbmn") String tradeAmount
    ) {}
}
