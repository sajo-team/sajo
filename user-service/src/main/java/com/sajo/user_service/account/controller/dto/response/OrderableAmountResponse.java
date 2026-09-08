package com.sajo.user_service.account.controller.dto.response;

import com.sajo.user_service.account.client.kis.dto.response.KisOrderableAmountDetailResponse;

public record OrderableAmountResponse(
        Long orderableAmount // 미수없는매수금액 (KIS: nrcvb_buy_amt)
) {
    public static OrderableAmountResponse from(KisOrderableAmountDetailResponse output) {
        return new OrderableAmountResponse(Long.parseLong(output.nrcvb_buy_amt()));
    }
}
