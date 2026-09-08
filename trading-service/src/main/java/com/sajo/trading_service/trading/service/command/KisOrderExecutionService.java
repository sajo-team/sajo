package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.client.AccountClient;
import com.sajo.trading_service.trading.client.KisOrderClient;
import com.sajo.trading_service.trading.client.dto.response.AccountOrderInfoResponse;
import com.sajo.trading_service.trading.client.dto.response.AccountTokenResponse;
import com.sajo.trading_service.trading.client.dto.response.KisOrderInquiryItem;
import com.sajo.trading_service.trading.client.dto.response.KisOrderInquiryResponse;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.query.OrderQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisOrderExecutionService {

    private static final String KIS_VIRTUAL_INQUIRY_TR_ID = "VTTC0081R";
    private static final String CUSTOMER_TYPE = "P";
    private static final String KRX_EXCHANGE_CODE = "KRX";

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderQueryRepository orderQueryRepository;
    private final AccountClient accountClient;
    private final KisOrderClient kisOrderClient;
    private final OrderExecutionCommandService orderExecutionCommandService;

    public void processExecution(UUID orderId) {

        Order order =
                orderQueryRepository.findByIdAndDeletedAtIsNull(orderId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        TradingErrorCode.ORDER_NOT_FOUND
                                )
                        );

        /*
         * 실제 주문 접수가 확인된 주문만 체결 조회 대상으로 사용한다.
         */
        if (order.getStatus() != OrderStatus.ACCEPTED
                && order.getStatus() != OrderStatus.PARTIALLY_FILLED) {

            log.info(
                    "KIS 체결 조회 대상이 아닌 주문입니다. orderId={}, status={}",
                    orderId,
                    order.getStatus()
            );
            return;
        }

        /*
         * ACCEPTED / PARTIALLY_FILLED 상태라면
         * brokerOrderNo가 존재해야 정상적인 체결 조회가 가능하다.
         */
        if (order.getBrokerOrderNo() == null
                || order.getBrokerOrderNo().isBlank()) {

            log.warn(
                    "체결 조회 대상 주문에 KIS 주문번호가 없습니다. orderId={}",
                    orderId
            );
            return;
        }

        AccountTokenResponse tokenResponse;
        AccountOrderInfoResponse accountInfo;

        try {
            tokenResponse =
                    accountClient.getAccessToken(order.getUserId());

            accountInfo =
                    accountClient.getOrderInfo(order.getUserId());
        } catch (RuntimeException e) {
            /*
             * 외부 인프라 조회 실패만으로 주문 상태를 변경하지 않는다.
             */
            log.warn(
                    "KIS 체결 조회를 위한 계좌 정보 조회에 실패했습니다. orderId={}",
                    orderId,
                    e
            );
            return;
        }

        String orderDate =
                order.getCreatedAt()
                        .atZone(ZoneId.of("Asia/Seoul"))
                        .format(DATE_FORMATTER);

        String sellBuyDivisionCode =
                order.getOrderType() == OrderType.BUY
                        ? "02"
                        : "01";

        KisOrderInquiryResponse response;

        try {
            response =
                    kisOrderClient.inquireDailyOrders(
                            "Bearer " + tokenResponse.accessToken(),
                            tokenResponse.appKey(),
                            tokenResponse.secretKey(),
                            KIS_VIRTUAL_INQUIRY_TR_ID,
                            CUSTOMER_TYPE,
                            accountInfo.cano(),
                            accountInfo.accountProductCode(),
                            orderDate,
                            orderDate,
                            sellBuyDivisionCode,
                            order.getStockCode(),
                            "",
                            order.getBrokerOrderNo(),
                            "00",
                            "00",
                            "",
                            "00",
                            KRX_EXCHANGE_CODE,
                            "",
                            ""
                    );
        } catch (RuntimeException e) {
            log.warn(
                    "KIS 체결 조회 중 오류가 발생했습니다. orderId={}",
                    orderId,
                    e
            );
            return;
        }

        orderExecutionCommandService.markExecutionChecked(
                orderId,
                Instant.now()
        );

        if (response == null || !"0".equals(response.rtCd())) {
            log.warn(
                    "KIS 체결 조회 실패 응답입니다. orderId={}, msgCd={}, message={}",
                    orderId,
                    response == null ? null : response.msgCd(),
                    response == null ? null : response.message()
            );
            return;
        }

        KisOrderInquiryItem item =
                findMatchedItem(
                        response.output1(),
                        order.getBrokerOrderNo()
                );

        if (item == null) {
            log.warn(
                    "KIS 체결 조회 결과에서 주문번호가 일치하는 주문을 찾지 못했습니다. orderId={}, brokerOrderNo={}",
                    orderId,
                    order.getBrokerOrderNo()
            );
            return;
        }

        processMatchedExecution(
                orderId,
                item
        );
    }

    private KisOrderInquiryItem findMatchedItem(
            List<KisOrderInquiryItem> items,
            String brokerOrderNo
    ) {
        if (items == null || items.isEmpty()) {
            return null;
        }

        List<KisOrderInquiryItem> matchedItems =
                items.stream()
                        .filter(item ->
                                brokerOrderNo.equals(item.orderNo())
                        )
                        .toList();

        if (matchedItems.size() != 1) {
            return null;
        }

        return matchedItems.get(0);
    }

    void processMatchedExecution(
            UUID orderId,
            KisOrderInquiryItem item
    ) {
        int totalFilledQuantity;
        int remainingQuantity;
        BigDecimal averageExecutionPrice;
        long totalExecutionAmount;
        int rejectedQuantity;

        try {
            totalFilledQuantity =
                    parseInteger(item.totalFilledQuantity());

            remainingQuantity =
                    parseInteger(item.remainingQuantity());

            averageExecutionPrice =
                    parseDecimal(item.averageExecutionPrice());

            totalExecutionAmount =
                    parseLong(item.totalExecutionAmount());

            rejectedQuantity =
                    parseInteger(item.rejectedQuantity());

        } catch (ArithmeticException | NumberFormatException | NullPointerException e) {
            log.warn(
                    "KIS 체결 정보 파싱에 실패했습니다. orderId={}, orderNo={}",
                    orderId,
                    item.orderNo()
            );
            return;
        }

        if ("Y".equalsIgnoreCase(item.canceled())) {
            try {
                orderExecutionCommandService.applyCancellation(
                        orderId,
                        totalFilledQuantity,
                        remainingQuantity,
                        averageExecutionPrice,
                        totalExecutionAmount
                );

                log.info(
                        "KIS 취소 주문 결과를 반영했습니다. orderId={}, orderNo={}, filledQuantity={}",
                        orderId,
                        item.orderNo(),
                        totalFilledQuantity
                );
            } catch (BusinessException e) {
                /*
                 * 주문 상태 불일치, 체결 수량 역전,
                 * 취소 주문의 잔여 수량 오류 등 비정상 응답은
                 * 임의로 Order 상태를 변경하지 않는다.
                 */
                log.warn(
                        "KIS 취소 결과를 Order에 반영할 수 없습니다. orderId={}, orderNo={}",
                        orderId,
                        item.orderNo(),
                        e
                );
            }

            return;

        }

        if (rejectedQuantity > 0) {
            log.warn(
                    "KIS 주문에 거절 수량이 포함되어 있어 체결 반영을 보류합니다. orderId={}, orderNo={}, rejectedQuantity={}",
                    orderId,
                    item.orderNo(),
                    rejectedQuantity
            );
            return;
        }

        if (totalFilledQuantity == 0) {
            /*
             * 아직 체결되지 않은 ACCEPTED 주문이다.
             */
            return;
        }

        try {
            orderExecutionCommandService.applyExecution(
                    orderId,
                    totalFilledQuantity,
                    remainingQuantity,
                    averageExecutionPrice,
                    totalExecutionAmount
            );
        } catch (BusinessException e) {
            /*
             * 체결 수량 역전, 주문 수량 초과 등의 비정상 응답은
             * 임의로 Order 상태를 변경하지 않는다.
             */
            log.warn(
                    "KIS 체결 결과를 Order에 반영할 수 없습니다. orderId={}, orderNo={}",
                    orderId,
                    item.orderNo(),
                    e
            );
        }
    }

    private int parseInteger(String value) {
        return new BigDecimal(value)
                .intValueExact();
    }

    private long parseLong(String value) {
        return new BigDecimal(value)
                .longValueExact();
    }

    private BigDecimal parseDecimal(String value) {
        return new BigDecimal(value);
    }
}