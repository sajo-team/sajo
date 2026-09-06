package com.sajo.trading_service.trading.client.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * KIS 주식일별주문체결조회 API 응답 DTO.
 *
 * 미확정 상태(PROCESSING, TIMEOUT)의 Order를
 * KIS 실제 주문 내역과 대조하여 상태를 보정할 때 사용한다.
 */
public record KisOrderInquiryResponse(

        /**
         * KIS 응답 성공 여부 코드.
         * "0"이면 정상 응답.
         */
        @JsonProperty("rt_cd")
        String rtCd,

        /**
         * KIS 응답 메시지 코드.
         */
        @JsonProperty("msg_cd")
        String msgCd,

        /**
         * KIS 응답 메시지.
         */
        @JsonProperty("msg1")
        String message,

        /**
         * 연속 조회 시 사용하는 이전 조회 컨텍스트 값.
         * 최초 조회 시에는 공백이며,
         * 추가 페이지 조회가 필요한 경우 다음 요청에 전달한다.
         */
        @JsonProperty("ctx_area_fk100")
        String ctxAreaFk100,

        /**
         * 연속 조회 시 사용하는 다음 조회 컨텍스트 값.
         * 조회 결과가 여러 페이지로 나뉘는 경우 사용한다.
         */
        @JsonProperty("ctx_area_nk100")
        String ctxAreaNk100,

        /**
         * 개별 주문/체결 조회 결과 목록.
         *
         * 내부 Order와 KIS 주문 내역을 매칭할 때 사용한다.
         */
        @JsonProperty("output1")
        List<KisOrderInquiryItem> output1
) {
}