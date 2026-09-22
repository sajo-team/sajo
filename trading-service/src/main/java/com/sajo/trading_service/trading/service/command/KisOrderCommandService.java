package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.feign.FeignApiException;
import com.sajo.common.response.GeneralResponse;
import com.sajo.trading_service.trading.client.AccountClient;
import com.sajo.trading_service.trading.client.KisOrderClient;
import com.sajo.trading_service.trading.client.MarketStockClient;
import com.sajo.trading_service.trading.client.dto.request.KisOrderRequest;
import com.sajo.trading_service.trading.client.dto.response.*;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.AccountType;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.validation.KisOrderPriceValidator;
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
    private final MarketStockClient marketStockClient;
    private final KisOrderPriceValidator kisOrderPriceValidator;

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

            if (infoResponse.accountType() != AccountType.VIRTUAL) {
                orderStatusCommandService.fail(
                        orderId,
                        "UNSUPPORTED_ACCOUNT_TYPE",
                        "모의투자 계좌만 주문할 수 있습니다."
                );
                return;
            }

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
                    "계좌 정보를 확인하는 중 오류가 발생했습니다."
            );
            return;

        } catch (BusinessException e) {
            orderStatusCommandService.fail(
                    orderId,
                    "ACCOUNT_SERVICE_ERROR",
                    "계좌 정보를 확인하는 중 오류가 발생했습니다."
            );
            return;

        } catch (RetryableException e) {
            log.warn(
                    "Account Service 일시 장애로 주문을 재시도 상태로 복구합니다. orderId={}",
                    orderId,
                    e
            );

            orderStatusCommandService.retry(orderId);
            return;

        } catch (FeignException e) {

            if (e.status() >= 500 || e.status() == 429) {
                log.warn(
                        "Account Service 일시 오류로 주문을 재시도 상태로 복구합니다. orderId={}, status={}",
                        orderId,
                        e.status(),
                        e
                );

                orderStatusCommandService.retry(orderId);

            } else {
                orderStatusCommandService.fail(
                        orderId,
                        "ACCOUNT_SERVICE_HTTP_" + e.status(),
                        "계좌 정보를 확인하는 중 오류가 발생했습니다."
                );
            }

            return;

        } catch (RuntimeException e) {
            log.error(
                    "Account Service 처리 중 예상하지 못한 오류가 발생했습니다. orderId={}",
                    orderId,
                    e
            );

            try {
                orderStatusCommandService.fail(
                        orderId,
                        "ACCOUNT_UNEXPECTED_ERROR",
                        "계좌 정보 처리 중 예상하지 못한 오류가 발생했습니다."
                );
            } catch (RuntimeException statusException) {
                log.error(
                        "Account 예상하지 못한 오류 후 FAILED 상태 저장 실패. orderId={}",
                        orderId,
                        statusException
                );
            }

            throw e;
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
         * 주문 가격 사전 검증
         */
        GeneralResponse<MarketStockQuoteResponse> marketResponse;

        try {
            marketResponse =
                    marketStockClient.getQuote(
                            order.getUserId(),
                            order.getStockCode()
                    );

        } catch (RetryableException e) {
            log.warn(
                    "Market Service 일시 장애로 주문을 재시도 상태로 복구합니다. orderId={}",
                    orderId,
                    e
            );

            orderStatusCommandService.retryMarketQuote(
                    orderId,
                    "MARKET_QUOTE_RETRY_EXHAUSTED",
                    "시세 정보 조회 재시도 횟수를 초과했습니다."
            );

            return;

        } catch (FeignApiException e) {
            log.warn(
                    "Market Service 비즈니스 오류가 발생했습니다. orderId={}, errorCode={}",
                    orderId,
                    e.getErrorCode(),
                    e
            );

            orderStatusCommandService.fail(
                    orderId,
                    e.getErrorCode(),
                    "주문 가격 검증을 위한 시세 정보를 확인할 수 없습니다."
            );

            return;

        }  catch (FeignException e) {

            if (e.status() >= 500 || e.status() == 429) {
                log.warn(
                        "Market Service 일시 오류로 주문을 재시도 상태로 복구합니다. orderId={}, status={}",
                        orderId,
                        e.status(),
                        e
                );

                orderStatusCommandService.retryMarketQuote(
                        orderId,
                        "MARKET_QUOTE_RETRY_EXHAUSTED",
                        "시세 정보 조회 재시도 횟수를 초과했습니다."
                );

            } else {
                orderStatusCommandService.fail(
                        orderId,
                        "MARKET_SERVICE_HTTP_" + e.status(),
                        "주문 가격 검증을 위한 시세 정보를 확인할 수 없습니다."
                );
            }

            return;

        } catch (RuntimeException e) {
            log.error(
                    "Market Service 처리 중 예상하지 못한 오류가 발생했습니다. orderId={}",
                    orderId,
                    e
            );

            try {
                orderStatusCommandService.fail(
                        orderId,
                        "MARKET_QUOTE_UNEXPECTED_ERROR",
                        "시세 정보 처리 중 예상하지 못한 오류가 발생했습니다."
                );
            } catch (RuntimeException statusException) {
                log.error(
                        "Market 예상하지 못한 오류 후 FAILED 상태 저장 실패. orderId={}",
                        orderId,
                        statusException
                );
            }

            throw e;
        }

        /*
         * GeneralResponse envelope 검증 및 data 추출
         */
        if (marketResponse == null || marketResponse.data() == null) {
            orderStatusCommandService.fail(
                    orderId,
                    "ORDER_PRICE_VALIDATION_UNAVAILABLE",
                    "주문 가격 검증에 필요한 시세 정보를 확인할 수 없습니다."
            );
            return;
        }

        MarketStockQuoteResponse quoteResponse =
                marketResponse.data();

        Long previousClosePrice = quoteResponse.previousClosePrice();

        if (previousClosePrice == null || previousClosePrice <= 0) {
            orderStatusCommandService.fail(
                    orderId,
                    "ORDER_PRICE_VALIDATION_UNAVAILABLE",
                    "주문 가격 검증에 필요한 시세 정보를 확인할 수 없습니다."
            );
            return;
        }

        /*
         * 신호가(signalPrice)를 호가단위(틱)에 맞도록 보정한다.
         *
         * KIS 실시간 체결가는 NXT 통합 이후 KRX보다 촘촘한 틱으로 체결된 가격을 내려줄 수 있어,
         * 신호가를 그대로 주문가로 쓰면 KRX 호가단위 기준으로는 유효하지 않은 가격이 되어 거래소가
         * 주문을 거부할 수 있다(#315). 실제 주문 접수 전에 가장 가까운 유효 틱으로 스냅한다.
         */
        long orderPrice = kisOrderPriceValidator.snapToTickSize(order.getSignalPrice(), order.getOrderType());

        if (orderPrice != order.getSignalPrice()) {
            log.info(
                    "신호가가 호가단위에 맞지 않아 주문가를 보정했습니다. orderId={}, signalPrice={}, snappedOrderPrice={}",
                    orderId,
                    order.getSignalPrice(),
                    orderPrice
            );
        }

        if (!kisOrderPriceValidator.isValidTickSize(orderPrice)) {
            orderStatusCommandService.fail( // 호가 단위 검증(스냅 이후에도 실패하면 방어적으로 차단)
                    orderId,
                    "INVALID_ORDER_TICK_SIZE",
                    "주문 가격이 해당 가격대의 호가단위에 맞지 않습니다."
            );
            return;
        }

        if (!kisOrderPriceValidator.isWithinDailyPriceLimit(
                orderPrice,
                previousClosePrice
        )) {
            orderStatusCommandService.fail( // 상·하한가 검증
                    orderId,
                    "ORDER_PRICE_OUT_OF_RANGE",
                    "주문 가격이 당일 허용 가격 범위를 벗어났습니다."
            );
            return;
        }

        /*
         * 검증을 통과한 실제 접수가를 기록한다(#315). signalPrice는 신호 발생 시점의 원본 값을
         * 이력으로 보존하고, 이 시점부터는 executedOrderPrice가 "실제로 KIS에 넣은 가격"의
         * 단일 진실 공급원(source of truth)이 된다 — 조회 응답과 재조정 매칭 모두 이 값을 본다.
         */
        orderStatusCommandService.recordExecutedOrderPrice(orderId, orderPrice);

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
                        Long.toString(orderPrice)
                );

        String trId =
                order.getOrderType() == OrderType.BUY
                        ? "VTTC0012U"
                        : "VTTC0011U";

        /*
         * KIS 주문 실행
         */
        KisOrderResponse response;

        try {
            response =
                    kisOrderClient.placeOrder(
                            "Bearer " + tokenResponse.accessToken(),
                            tokenResponse.appKey(),
                            tokenResponse.secretKey(),
                            trId,
                            "P",
                            request
                    );

        } catch (RetryableException e) {

            // 네트워크/Timeout 등으로 실제 주문 접수 여부를 확신할 수 없음
            orderStatusCommandService.timeout(
                    orderId,
                    "KIS_TIMEOUT",
                    "KIS 주문 응답을 확인할 수 없습니다."
            );
            return;

        } catch (FeignException e) {

            int status = e.status();

            if (status >= 500) {
                orderStatusCommandService.timeout(
                        orderId,
                        "KIS_SERVER_ERROR",
                        "KIS 서버 오류로 주문 결과를 확인할 수 없습니다."
                );

            } else if (status == 401
                    || status == 403
                    || status == 429) {

                orderStatusCommandService.timeout(
                        orderId,
                        "KIS_RETRYABLE_HTTP_" + status,
                        "KIS 주문 요청을 일시적으로 처리할 수 없습니다."
                );

            } else {
                orderStatusCommandService.fail(
                        orderId,
                        "KIS_HTTP_" + status,
                        "KIS 주문 요청이 실패했습니다."
                );
            }

            return;

        } catch (RuntimeException e) {
            log.error(
                    "KIS 주문 처리 중 예상하지 못한 오류가 발생했습니다. orderId={}",
                    orderId,
                    e
            );

            try {
                orderStatusCommandService.timeout(
                        orderId,
                        "KIS_UNEXPECTED_ERROR",
                        "KIS 주문 처리 중 예상하지 못한 오류가 발생했습니다."
                );
            } catch (RuntimeException statusException) {
                log.error(
                        "KIS 예상하지 못한 오류 후 TIMEOUT 상태 저장 실패. orderId={}",
                        orderId,
                        statusException
                );
            }

            throw e;
        }

        /*
         * 여기부터는 KIS 응답을 정상적으로 받은 이후의 상태 처리
         */
        if ("0".equals(response.rtCd())) {

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
                 * KIS 주문은 성공했지만 ACCEPTED 상태 반영에 실패한 경우.
                 * 실제 주문은 접수되었을 수 있으므로 TIMEOUT 전이를 시도하고
                 * 이후 주문 조회를 통해 상태를 보정한다.
                 */
                log.error(
                        "KIS 주문 성공 후 Order 상태 저장 실패. orderId={}, brokerOrderNo={}",
                        orderId,
                        response.output().orderNo(),
                        e
                );

                orderStatusCommandService.timeoutWithBrokerOrderNo(
                        orderId,
                        response.output().orderNo(),
                        "KIS_ACCEPT_SAVE_ERROR",
                        "KIS 주문은 접수되었으나 주문 상태 저장에 실패했습니다."
                );
            }

        } else {

            /*
             * KIS로부터 명확한 실패 응답을 받은 상태.
             * fail() 자체의 DB 저장 실패를 KIS_UNKNOWN_ERROR / TIMEOUT으로
             * 오분류하지 않도록 KIS 호출 try-catch 밖에서 처리한다.
             */
            try {
                orderStatusCommandService.fail(
                        orderId,
                        response.msgCd(),
                        response.message()
                );

            } catch (RuntimeException e) {
                log.error(
                        "KIS 주문 실패 응답 후 FAILED 상태 저장 실패. orderId={}, msgCd={}",
                        orderId,
                        response.msgCd(),
                        e
                );

                throw e;
            }
        }
    }
}