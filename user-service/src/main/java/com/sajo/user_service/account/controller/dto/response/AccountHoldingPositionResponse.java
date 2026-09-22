package com.sajo.user_service.account.controller.dto.response;

import com.sajo.user_service.account.client.kis.dto.response.KisBalanceHoldingResponse;

import java.math.BigDecimal;

public record AccountHoldingPositionResponse(
        Long quantity, // 보유수량 (KIS: hldg_qty)
        BigDecimal avgPurchasePrice, // 매입평균가격 - 매입금액 / 보유수량 (KIS: pchs_avg_pric)
        BigDecimal profitLossRate // 평가손익율(%) (KIS: evlu_pfls_rt)
) {
    public static AccountHoldingPositionResponse from(KisBalanceHoldingResponse holding) {
        return new AccountHoldingPositionResponse(
                Long.parseLong(holding.hldg_qty()),
                new BigDecimal(holding.pchs_avg_pric()),
                new BigDecimal(holding.evlu_pfls_rt())
        );
    }
}
