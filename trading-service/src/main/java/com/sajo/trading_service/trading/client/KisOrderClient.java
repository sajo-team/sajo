package com.sajo.trading_service.trading.client;

import com.sajo.trading_service.trading.client.dto.request.KisOrderRequest;
import com.sajo.trading_service.trading.client.dto.response.KisOrderResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "kis-order-client",
        url = "${kis.base-url}"
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
}
