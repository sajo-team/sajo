package com.sajo.user_service.account.controller.dto.response;

import com.sajo.user_service.account.client.dto.response.KisBalanceSummaryResponse;

import java.time.Instant;

public record AccountDepositResponse(
        Long depositTotal, // 예수금총금액 - 계좌에 있는 현금 총액 (KIS: dnca_tot_amt)
        Long d1Deposit, // D+1 예수금 - 내일 정산되는 금액 (KIS: nxdy_excc_amt)
        Long d2Deposit, // D+2 예수금 - 모레 정산되는 금액 (KIS: prvs_rcdl_excc_amt)
        Long totalEvaluationAmount, // 총평가금액 - 보유종목 평가금액 합계 + D+2 예수금 (KIS: tot_evlu_amt)
        Long netAssetAmount, // 순자산금액 (KIS: nass_amt)
        Long totalProfitLoss, // 평가손익합계금액 - 보유종목 전체 평가손익 총합 (KIS: evlu_pfls_smtl_amt)
        Instant asOf // 조회 기준 시각 (KIS 응답에는 없음 - 조회 시점에 직접 채움)
) {
    public static AccountDepositResponse from(KisBalanceSummaryResponse summary, Instant asOf) {
        return new AccountDepositResponse(
                Long.parseLong(summary.dnca_tot_amt()),
                Long.parseLong(summary.nxdy_excc_amt()),
                Long.parseLong(summary.prvs_rcdl_excc_amt()),
                Long.parseLong(summary.tot_evlu_amt()),
                Long.parseLong(summary.nass_amt()),
                Long.parseLong(summary.evlu_pfls_smtl_amt()),
                asOf
        );
    }
}
