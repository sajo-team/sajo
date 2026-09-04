package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.client.AccountClient;
import com.sajo.trading_service.trading.client.KisOrderClient;
import com.sajo.trading_service.trading.client.dto.request.KisOrderRequest;
import com.sajo.trading_service.trading.client.dto.response.*;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.command.OrderCommandRepository;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KisOrderCommandService {
    private final OrderCommandRepository orderCommandRepository;
    private final AccountClient accountClient;
    private final KisOrderClient kisOrderClient;
    private final OrderStatusCommandService  orderStatusCommandService;

    public void executeOrder(UUID orderId){
        Order order =
                orderCommandRepository
                        .findById(orderId)
                        .orElseThrow(()->
                                new BusinessException(
                                        TradingErrorCode.ORDER_NOT_FOUND
                                )
                        );

        AccountTokenResponse tokenResponse =
                accountClient.getAccessToken(order.getUserId());

        AccountOrderInfoResponse infoResponse =
                accountClient.getOrderInfo(order.getUserId());

        if(order.getOrderType() == OrderType.BUY) {
            AccountOrderableAmountResponse amountResponse =
                    accountClient.getOrderableAmount(order.getUserId());

            if (amountResponse.orderableAmount() < order.getEstimatedOrderAmount()) {
                throw new BusinessException(
                        TradingErrorCode.ORDERABLE_AMOUNT_NOT_ENOUGH
                );
            }
        } else if (order.getOrderType() == OrderType.SELL) {
            AccountHoldingResponse holdingResponse =
                    accountClient.getHolding(order.getUserId(), order.getStockCode());

            if (holdingResponse.sellableQuantity() < order.getOrderQuantity()) {
                throw new BusinessException(
                        TradingErrorCode.SELLABLE_QUANTITY_NOT_ENOUGH
                );
            }
        }

        KisOrderRequest request =
                new KisOrderRequest(
                        infoResponse.cano(),
                        infoResponse.accountProductCode(),
                        order.getStockCode(),
                        "00",
                        order.getOrderQuantity().toString(),
                        order.getSignalPrice().toString()
                );

        String trId;

        if(order.getOrderType() == OrderType.BUY){
            trId = "VTTC0012U";
        } else {
            trId = "VTTC0011U";
        }

        try {
            KisOrderResponse response =
                    kisOrderClient.placeOrder(
                            "Bearer " + tokenResponse.accessToken(),
                            tokenResponse.appKey(),
                            tokenResponse.secretKey(),
                            trId,
                            "P",
                            request
                    );

            if("0".equals(response.rtCd())){
                orderStatusCommandService.accept(orderId, response.output().orderNo());
            } else {
                orderStatusCommandService.fail(orderId, response.msgCd(), response.message());
            }
        } catch (RetryableException e){
            // TODO: Feign timeout/네트워크 예외를 세분화하여 TIMEOUT 처리 기준 보완
            orderStatusCommandService.timeout(
                    orderId,
                    "KIS_TIMEOUT",
                    "KIS 주문 응답 TIMEOUT 발생"
            );
        }
    }
}
