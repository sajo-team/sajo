package com.sajo.trading_service.trading.client.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisOrderResponse(
        @JsonProperty("rt_cd")
        String rtCd, // KIS 요청 성공/실패 여부 코드

        @JsonProperty("msg_cd")
        String msgCd, // KIS 응답 코드. 실패 시 failureCode로 저장 가능

        @JsonProperty("msg1")
        String message, // KIS 응답 메시지. 실패 시 failureMessage로 저장 가능

        @JsonProperty("output")
        KisOrderOutput output // 주문 성공 시 주문번호/주문시간 등 상세 결과
) {
    public record KisOrderOutput(
            @JsonProperty("ODNO")
            String orderNo, // KIS 주문번호 → Order.brokerOrderNo에 저장

            @JsonProperty("ORD_TMD")
            String orderTime // KIS 주문 처리 시각(HHmmss 형태)
    ){
    }
}
