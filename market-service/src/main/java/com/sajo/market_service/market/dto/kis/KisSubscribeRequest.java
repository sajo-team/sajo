package com.sajo.market_service.market.dto.kis;

import com.fasterxml.jackson.annotation.JsonProperty;

/** KIS WebSocket 실시간 체결가(H0STCNT0) 구독 등록 요청 프레임. */
public record KisSubscribeRequest(Header header, Body body) {

    private static final String TRADE_CONDITION_TR_ID = "H0STCNT0";
    private static final String CUSTOMER_TYPE_PERSONAL = "P";
    private static final String REGISTER_TR_TYPE = "1";
    private static final String CONTENT_TYPE = "utf-8";

    public static KisSubscribeRequest of(String approvalKey, String stockCode) {
        return new KisSubscribeRequest(
                new Header(approvalKey, CUSTOMER_TYPE_PERSONAL, REGISTER_TR_TYPE, CONTENT_TYPE),
                new Body(new Input(TRADE_CONDITION_TR_ID, stockCode))
        );
    }

    public record Header(
            @JsonProperty("approval_key") String approvalKey,
            String custtype,
            @JsonProperty("tr_type") String trType,
            @JsonProperty("content-type") String contentType
    ) {
    }

    public record Body(Input input) {
    }

    public record Input(
            @JsonProperty("tr_id") String trId,
            @JsonProperty("tr_key") String trKey
    ) {
    }
}
