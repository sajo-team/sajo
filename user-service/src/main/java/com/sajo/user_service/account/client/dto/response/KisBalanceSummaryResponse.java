package com.sajo.user_service.account.client.dto.response;

// KIS 주식잔고조회 output2 (예수금 등 계좌 요약, 배열이지만 실질적으로 원소 1개)
public record KisBalanceSummaryResponse(
        String dnca_tot_amt, // 예수금총금액 - 예수금
        String nxdy_excc_amt, // 익일정산금액 - D+1 예수금
        String prvs_rcdl_excc_amt, // 가수도정산금액 - D+2 예수금
        String cma_evlu_amt, // CMA평가금액
        String bfdy_buy_amt, // 전일매수금액
        String thdt_buy_amt, // 금일매수금액
        String nxdy_auto_rdpt_amt, // 익일자동상환금액
        String bfdy_sll_amt, // 전일매도금액
        String thdt_sll_amt, // 금일매도금액
        String d2_auto_rdpt_amt, // D+2자동상환금액
        String bfdy_tlex_amt, // 전일제비용금액
        String thdt_tlex_amt, // 금일제비용금액
        String tot_loan_amt, // 총대출금액
        String scts_evlu_amt, // 유가평가금액
        String tot_evlu_amt, // 총평가금액 - 유가증권 평가금액 합계금액 + D+2 예수금
        String nass_amt, // 순자산금액
        String fncg_gld_auto_rdpt_yn, // 융자금자동상환여부 - 보유현금에 대한 융자금만 차감여부
        String pchs_amt_smtl_amt, // 매입금액합계금액
        String evlu_amt_smtl_amt, // 평가금액합계금액 - 유가증권 평가금액 합계금액
        String evlu_pfls_smtl_amt, // 평가손익합계금액
        String tot_stln_slng_chgs, // 총대주매각대금
        String bfdy_tot_asst_evlu_amt, // 전일총자산평가금액
        String asst_icdc_amt, // 자산증감액
        String asst_icdc_erng_rt // 자산증감수익율 - 데이터 미제공
) {
}
