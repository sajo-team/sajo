package com.sajo.trading_service.trading.client.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisOrderRequest(
        @JsonProperty("CANO")
        String cano, // KIS 계좌번호 앞 8자리

        @JsonProperty("ACNT_PRDT_CD")
        String accountProductCode, // 계좌상품코드, 계좌번호 뒤 2자리

        @JsonProperty("PDNO")
        String productCode, // 종목코드 (예: 삼성전자 005930)

        @JsonProperty("ORD_DVSN")
        String orderDivision, // 주문구분코드 ("00" = 지정가, "01" = 시장가)

        @JsonProperty("ORD_QTY")
        String orderQuantity, // 주문 수량

        @JsonProperty("ORD_UNPR")
        String orderPrice // 주문 단가, 지정가 주문 시 사용
) {
}
