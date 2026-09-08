package com.sajo.trading_service.trading.client.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * KIS 주식일별주문체결조회 API의 개별 주문 결과 DTO.
 *
 * brokerOrderNo가 존재하는 경우 주문번호로 직접 매칭하고,
 * 주문번호가 없는 TIMEOUT 주문은
 * 종목/매수매도/수량/가격/주문시간 등을 조합하여 매칭한다.
 */
public record KisOrderInquiryItem(

        /**
         * 주문 일자.
         * 형식: yyyyMMdd
         */
        @JsonProperty("ord_dt")
        String orderDate,

        /**
         * 주문채번지점번호.
         * KIS 주문 조회 및 주문 식별에 사용되는 값.
         */
        @JsonProperty("ord_gno_brno")
        String orderBranchNo,

        /**
         * KIS 주문번호.
         *
         * 내부 Order의 brokerOrderNo와 매칭하거나,
         * brokerOrderNo가 없는 경우 복구할 때 사용한다.
         */
        @JsonProperty("odno")
        String orderNo,

        /**
         * 매도/매수 구분 코드.
         *
         * 내부 OrderType(BUY/SELL)과 매칭할 때 사용한다.
         */
        @JsonProperty("sll_buy_dvsn_cd")
        String sellBuyDivisionCode,

        /**
         * 종목 코드.
         *
         * 내부 Order.stockCode와 비교하여 주문 후보를 좁힌다.
         */
        @JsonProperty("pdno")
        String stockCode,

        /**
         * 주문 수량.
         *
         * 내부 Order.orderQuantity와 비교한다.
         * KIS 응답 계약을 그대로 유지하기 위해 String으로 수신한다.
         */
        @JsonProperty("ord_qty")
        String orderQuantity,

        /**
         * 주문 단가.
         *
         * 내부 Order.signalPrice와 비교한다.
         * KIS 응답 계약을 그대로 유지하기 위해 String으로 수신한다.
         */
        @JsonProperty("ord_unpr")
        String orderPrice,

        /**
         * 주문 시각.
         *
         * 주문번호가 없는 TIMEOUT Order를 매칭할 때
         * 내부 주문 생성 시각과 일정 시간 범위 내인지 확인하는 데 사용한다.
         */
        @JsonProperty("ord_tmd")
        String orderTime,

        /**
         * 총 체결 수량.
         *
         * 주문 체결 상태와 누적 체결 수량을 판단할 때 사용한다.
         */
        @JsonProperty("tot_ccld_qty")
        String totalFilledQuantity,

        /**
         * 평균 체결 가격.
         *
         * 주문의 누적 체결 평균 가격.
         */
        @JsonProperty("avg_prvs")
        String averageExecutionPrice,

/**
         * 총 체결 금액.
         *
         * 현재까지 누적된 체결 금액.
         */
        @JsonProperty("tot_ccld_amt")
        String totalExecutionAmount,

        /**
         * 아직 체결되지 않은 잔여 주문 수량.
         */
        @JsonProperty("rmn_qty")
        String remainingQuantity,

        /**
         * 거절된 주문 수량.
         *
         * 주문 실패가 명확한 경우 FAILED 상태 보정 판단에 활용할 수 있다.
         */
        @JsonProperty("rjct_qty")
        String rejectedQuantity,

        /**
         * 주문 취소 여부.
         *
         * 조회된 주문이 취소 주문인지 판단할 때 참고한다.
         */
        @JsonProperty("cncl_yn")
        String canceled
) {
}