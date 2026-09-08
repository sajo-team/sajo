package com.sajo.trading_service.trading.client;

import com.sajo.trading_service.trading.client.dto.request.KisOrderRequest;
import com.sajo.trading_service.trading.client.dto.response.KisOrderInquiryResponse;
import com.sajo.trading_service.trading.client.dto.response.KisOrderResponse;
import com.sajo.trading_service.trading.config.KisFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "kis-order-client",
        url = "${kis.base-url}",
        configuration = KisFeignConfiguration.class
)
public interface KisOrderClient {

    @PostMapping("/uapi/domestic-stock/v1/trading/order-cash")
    KisOrderResponse placeOrder(
            @RequestHeader("authorization") String authorization,
            @RequestHeader("appkey") String appKey,
            @RequestHeader("appsecret") String appSecret,
            @RequestHeader("tr_id") String trId,
            @RequestHeader("custtype") String customerType,
            @RequestBody KisOrderRequest request);


    @GetMapping("/uapi/domestic-stock/v1/trading/inquire-daily-ccld")
    KisOrderInquiryResponse inquireDailyOrders(
            @RequestHeader("authorization") String authorization,
            @RequestHeader("appkey") String appKey,
            @RequestHeader("appsecret") String appSecret,
            @RequestHeader("tr_id") String trId,
            @RequestHeader("custtype") String customerType,

            // 계좌번호 앞 8자리
            @RequestParam("CANO") String cano,

            // 계좌상품코드 뒤 2자리
            @RequestParam("ACNT_PRDT_CD") String accountProductCode,

            // 조회 시작일 yyyyMMdd
            @RequestParam("INQR_STRT_DT") String inquiryStartDate,

            // 조회 종료일 yyyyMMdd
            @RequestParam("INQR_END_DT") String inquiryEndDate,

            // 매도/매수 구분 코드
            @RequestParam("SLL_BUY_DVSN_CD") String sellBuyDivisionCode,

            // 종목코드
            @RequestParam("PDNO") String stockCode,

            // 주문채번지점번호.
            // 문서상 Required=Y이나 모의투자 조회에서는 빈 문자열로 정상 조회됨.
            @RequestParam("ORD_GNO_BRNO") String orderBranchNo,

            // KIS 주문번호. brokerOrderNo가 없는 TIMEOUT 주문은 빈 값 사용 여부 확인 필요.
            @RequestParam("ODNO") String orderNo,

            // 체결 구분
            @RequestParam("CCLD_DVSN") String conclusionDivision,

            // 조회 구분
            @RequestParam("INQR_DVSN") String inquiryDivision,

            // 조회 구분 1
            @RequestParam("INQR_DVSN_1") String inquiryDivision1,

            // 조회 구분 3
            @RequestParam("INQR_DVSN_3") String inquiryDivision3,

            // 거래소 구분
            @RequestParam("EXCG_ID_DVSN_CD") String exchangeDivisionCode,

            // 연속 조회용 FK
            @RequestParam("CTX_AREA_FK100") String contextAreaFk100,

            // 연속 조회용 NK
            @RequestParam("CTX_AREA_NK100") String contextAreaNk100
    );
}
