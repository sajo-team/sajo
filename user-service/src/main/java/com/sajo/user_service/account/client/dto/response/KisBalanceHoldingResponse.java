package com.sajo.user_service.account.client.dto.response;

// KIS 주식잔고조회 output1 (보유종목별 상세)
public record KisBalanceHoldingResponse(
        String pdno, // 상품번호 - 종목번호(뒷 6자리)
        String prdt_name, // 상품명 - 종목명
        String trad_dvsn_name, // 매매구분명 - 매수매도구분
        String bfdy_buy_qty, // 전일매수수량
        String bfdy_sll_qty, // 전일매도수량
        String thdt_buyqty, // 금일매수수량
        String thdt_sll_qty, // 금일매도수량
        String hldg_qty, // 보유수량
        String ord_psbl_qty, // 주문가능수량
        String pchs_avg_pric, // 매입평균가격 - 매입금액 / 보유수량
        String pchs_amt, // 매입금액
        String prpr, // 현재가
        String evlu_amt, // 평가금액
        String evlu_pfls_amt, // 평가손익금액 - 평가금액 - 매입금액
        String evlu_pfls_rt, // 평가손익율
        String evlu_erng_rt, // 평가수익율 - 미사용항목(0으로 출력)
        String loan_dt, // 대출일자 - INQR_DVSN(조회구분)을 01(대출일별)로 설정해야 값이 나옴
        String loan_amt, // 대출금액
        String stln_slng_chgs, // 대주매각대금
        String expd_dt, // 만기일자
        String fltt_rt, // 등락율
        String bfdy_cprs_icdc, // 전일대비증감
        String item_mgna_rt_name, // 종목증거금율명
        String grta_rt_name, // 보증금율명
        String sbst_pric, // 대용가격 - 증권매매의 위탁보증금으로서 현금 대신에 사용되는 유가증권 가격
        String stck_loan_unpr // 주식대출단가
) {
}
