package com.sajo.user_service.account.client.kis.dto.response;

// KIS 매수가능조회(inquire-psbl-order) output - 필요한 필드만 선별 매핑 (나머지는 Jackson이 무시함)
public record KisOrderableAmountDetailResponse(
        String nrcvb_buy_amt // 미수없는매수금액 - 미수(신용) 없이 매수 가능한 현금 기준 금액. 자동매매는 미수를 쓰지 않으므로 이 필드를 사용
) {
}
