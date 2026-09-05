package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.feign.FeignApiException;
import com.sajo.trading_service.trading.client.AccountClient;
import com.sajo.trading_service.trading.client.KisOrderClient;
import com.sajo.trading_service.trading.client.dto.request.KisOrderRequest;
import com.sajo.trading_service.trading.client.dto.response.*;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisOrderCommandService {

    private final AccountClient accountClient;
    private final KisOrderClient kisOrderClient;
    private final OrderStatusCommandService orderStatusCommandService;

    public void executeOrder(UUID orderId) {

        Order order =
                orderStatusCommandService.startProcessing(orderId);

        AccountTokenResponse tokenResponse;
        AccountOrderInfoResponse infoResponse;
        AccountOrderableAmountResponse amountResponse = null;
        AccountHoldingResponse holdingResponse = null;

        /*
         * Account Service 조회
         *
         * 아직 KIS 주문을 전송하기 전이므로
         * Account Service 호출 실패는 FAILED 처리한다.
         */
        try {
            tokenResponse =
                    accountClient.getAccessToken(order.getUserId());

            infoResponse =
                    accountClient.getOrderInfo(order.getUserId());

            if (order.getOrderType() == OrderType.BUY) {
                amountResponse =
                        accountClient.getOrderableAmount(order.getUserId());

            } else if (order.getOrderType() == OrderType.SELL) {
                holdingResponse =
                        accountClient.getHolding(
                                order.getUserId(),
                                order.getStockCode()
                        );
            }

        } catch (FeignApiException e) {
            orderStatusCommandService.fail(
                    orderId,
                    e.getErrorCode(),
                    e.getMessage()
            );
            return;

        } catch (BusinessException e) {
            orderStatusCommandService.fail(
                    orderId,
                    "ACCOUNT_SERVICE_ERROR",
                    e.getMessage()
            );
            return;

        } catch (FeignException e) {
            orderStatusCommandService.fail(
                    orderId,
                    "ACCOUNT_SERVICE_HTTP_" + e.status(),
                    "Account Service 호출에 실패했습니다."
            );
            return;
        } catch (RuntimeException e) {
            log.error(
                    "Account Service 처리 중 예상하지 못한 오류가 발생했습니다. orderId={}",
                    orderId,
                    e
            );

            orderStatusCommandService.fail(
                    orderId,
                    "ACCOUNT_SERVICE_UNKNOWN_ERROR",
                    "Account Service 처리 중 예상하지 못한 오류가 발생했습니다."
            );
            return;
        }

        /*
         * BUY / SELL 사전 검증
         */
        if (order.getOrderType() == OrderType.BUY) {

            Long orderableAmount =
                    amountResponse != null
                            ? amountResponse.orderableAmount()
                            : null;

            if (orderableAmount == null
                    || orderableAmount < order.getEstimatedOrderAmount()) {

                orderStatusCommandService.fail(
                        orderId,
                        "ORDERABLE_AMOUNT_NOT_ENOUGH",
                        "주문 가능 금액이 부족합니다."
                );
                return;
            }

        } else if (order.getOrderType() == OrderType.SELL) {

            Integer sellableQuantity =
                    holdingResponse != null
                            ? holdingResponse.sellableQuantity()
                            : null;

            if (sellableQuantity == null
                    || sellableQuantity < order.getOrderQuantity()) {

                orderStatusCommandService.fail(
                        orderId,
                        "SELLABLE_QUANTITY_NOT_ENOUGH",
                        "매도 가능 수량이 부족합니다."
                );
                return;
            }
        }

        /*
         * KIS 주문 요청 생성
         */
        KisOrderRequest request =
                new KisOrderRequest(
                        infoResponse.cano(),
                        infoResponse.accountProductCode(),
                        order.getStockCode(),
                        "00",
                        order.getOrderQuantity().toString(),
                        order.getSignalPrice().toString()
                );

        String trId =
                order.getOrderType() == OrderType.BUY
                        ? "VTTC0012U"
                        : "VTTC0011U";

        /*
         * KIS 주문 실행
         */
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

            if ("0".equals(response.rtCd())) {

                /*
                 * 성공 응답인데 주문번호가 없으면
                 * 실제 주문 결과를 확신할 수 없으므로 TIMEOUT 취급
                 */
                if (response.output() == null
                        || response.output().orderNo() == null
                        || response.output().orderNo().isBlank()) {

                    orderStatusCommandService.timeout(
                            orderId,
                            "KIS_INVALID_RESPONSE",
                            "KIS 주문 성공 응답의 주문번호를 확인할 수 없습니다."
                    );
                    return;
                }

                try {
                    orderStatusCommandService.accept(
                            orderId,
                            response.output().orderNo()
                    );

                } catch (RuntimeException e) {
                    /*
                     * KIS 주문은 성공했지만 DB 상태 반영이 실패한 경우.
                     * 예외를 상위 catch로 전달하여 TIMEOUT 전이를 시도하고,
                     * 이후 주문 조회를 통해 실제 주문 상태를 보정한다.
                     */
                    log.error(
                            "KIS 주문 성공 후 Order 상태 저장 실패. orderId={}, brokerOrderNo={}",
                            orderId,
                            response.output().orderNo(),
                            e
                    );

                    throw e;
                }

            } else {
                orderStatusCommandService.fail(
                        orderId,
                        response.msgCd(),
                        response.message()
                );
            }

        } catch (RetryableException e) {

            // 네트워크/Timeout 등으로 실제 주문 접수 여부를 확신할 수 없음
            orderStatusCommandService.timeout(
                    orderId,
                    "KIS_TIMEOUT",
                    "KIS 주문 응답을 확인할 수 없습니다."
            );

        } catch (FeignException e) {

            if (e.status() >= 500) {
                // KIS 내부에서 실제 주문이 처리됐을 가능성이 있으므로 결과 불확실
                orderStatusCommandService.timeout(
                        orderId,
                        "KIS_SERVER_ERROR",
                        "KIS 서버 오류로 주문 결과를 확인할 수 없습니다."
                );

            } else {
                // 4xx는 요청 거절이 명확하므로 FAILED
                orderStatusCommandService.fail(
                        orderId,
                        "KIS_HTTP_" + e.status(),
                        "KIS 주문 요청이 실패했습니다."
                );
            }
        } catch (RuntimeException e) {
            log.error(
                    "KIS 주문 처리 중 예상하지 못한 오류가 발생했습니다. orderId={}",
                    orderId,
                    e
            );

            orderStatusCommandService.timeout(
                    orderId,
                    "KIS_UNKNOWN_ERROR",
                    "KIS 주문 결과를 확인할 수 없습니다."
            );
        }
    }
}