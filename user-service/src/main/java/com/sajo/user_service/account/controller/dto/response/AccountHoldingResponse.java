package com.sajo.user_service.account.controller.dto.response;

import com.sajo.user_service.account.client.dto.response.KisBalanceHoldingResponse;

import java.math.BigDecimal;

public record AccountHoldingResponse(
        String stockCode, // 종목번호 (KIS: pdno)
        String stockName, // 종목명 (KIS: prdt_name)
        Long quantity, // 보유수량 (KIS: hldg_qty)
        Long sellableQuantity, // 매도가능수량 (KIS: ord_psbl_qty)
        BigDecimal avgPurchasePrice, // 매입평균가격 - 매입금액 / 보유수량 (KIS: pchs_avg_pric)
        Long currentPrice, // 현재가 (KIS: prpr)
        Long evaluationAmount, // 평가금액 (KIS: evlu_amt)
        Long profitLossAmount, // 평가손익금액 (KIS: evlu_pfls_amt)
        BigDecimal profitLossRate // 평가손익율(%) (KIS: evlu_pfls_rt)
) {
    public static AccountHoldingResponse from(KisBalanceHoldingResponse holding) {
        return new AccountHoldingResponse(
                holding.pdno(),
                holding.prdt_name(),
                Long.parseLong(holding.hldg_qty()),
                Long.parseLong(holding.ord_psbl_qty()),
                new BigDecimal(holding.pchs_avg_pric()),
                Long.parseLong(holding.prpr()),
                Long.parseLong(holding.evlu_amt()),
                Long.parseLong(holding.evlu_pfls_amt()),
                new BigDecimal(holding.evlu_pfls_rt())
        );
    }
}
