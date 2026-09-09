package com.sajo.user_service.account.client.kis.dto.response;

// KIS 매수가능조회(inquire-psbl-order) 응답 - rt_cd "0"이 아니면 업무상 실패 (HTTP 200이어도)
public record KisOrderableAmountResponse(
        String rt_cd,
        String msg_cd,
        String msg1,
        KisOrderableAmountDetailResponse output
) {
}
