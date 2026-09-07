package com.sajo.user_service.account.controller.dto.response;

import com.sajo.user_service.account.client.dto.response.KisBalanceHoldingResponse;

public record SellableQuantityResponse(
        Integer sellableQuantity // 매도가능수량 (KIS: ord_psbl_qty) - 보유하지 않은 종목이면 0
) {
    public static SellableQuantityResponse from(KisBalanceHoldingResponse holding) {
        return new SellableQuantityResponse(Integer.parseInt(holding.ord_psbl_qty()));
    }

    public static SellableQuantityResponse notHeld() {
        return new SellableQuantityResponse(0);
    }
}
