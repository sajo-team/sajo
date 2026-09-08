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
import com.sajo.trading_service.trading.reconciliation.KisOrderMatcher;
import com.sajo.trading_service.trading.reconciliation.MatchResult;
import com.sajo.trading_service.trading.repository.query.OrderQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisOrderReconciliationService {

    private static final String KIS_VIRTUAL_INQUIRY_TR_ID = "VTTC0081R";
    private static final String CUSTOMER_TYPE = "P";
    private static final String KRX_EXCHANGE_CODE = "KRX";

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderQueryRepository orderQueryRepository;
    private final AccountClient accountClient;
    private final KisOrderClient kisOrderClient;
    private final OrderStatusCommandService orderStatusCommandService;
    private final KisOrderMatcher kisOrderMatcher;

    public void reconcile(UUID orderId){
        Order order =
                orderQueryRepository.findByIdAndDeletedAtIsNull(orderId)
                        .orElseThrow(()->
                                new BusinessException(
                                        TradingErrorCode.ORDER_NOT_FOUND
                                )
                        );

        /*
         * PROCESSING / TIMEOUT 상태만 KIS 조회 기반 보정 대상이다.
         *
         * 이미 ACCEPTED / FAILED 등으로 상태가 확정된 주문은
         * 다시 조회하거나 변경하지 않는다.
         */

        if(order.getStatus() != OrderStatus.PROCESSING
                && order.getStatus() != OrderStatus.TIMEOUT){

            log.info(
                    "KIS 주문 보정 대상이 아닌 상태입니다. orderId={}, status={}",
                    orderId,
                    order.getStatus()
            );
            return;
        }

        /*
         * KIS 조회에 필요한 인증/계좌 정보 조회.
         */
        AccountTokenResponse tokenResponse;
        AccountOrderInfoResponse infoResponse;

        try {
            tokenResponse =
                    accountClient.getAccessToken(order.getUserId());

            infoResponse =
                    accountClient.getOrderInfo(order.getUserId());

        } catch (RuntimeException e) {
            log.warn(
                    "KIS 주문 보정을 위한 계좌 정보 조회 중 오류가 발생했습니다. orderId={}",
                    orderId,
                    e
            );

            orderStatusCommandService.recordReconciliationFailure(orderId);
            return;
        }

        /*
         * KIS 일별주문체결조회는 날짜 기준으로 조회한다.
         *
         * 현재는 내부 Order 생성일 기준 날짜를 사용한다.
         */

        String orderDate =
                order.getCreatedAt()
                        .atZone(ZoneId.of("Asia/Seoul"))
                        .format(DATE_FORMATTER);

        String orderBranchNo = "";

        String sellBuyDivisionCode =
                order.getOrderType() == OrderType.BUY
                ? "02"
                : "01";

        /*
         * brokerOrderNo가 없는 미확정 주문은 ODNO를 빈 문자열로 전달한다.
         *
         * KIS 모의투자 환경에서 ODNO="" 요청 시
         * 주문번호 필터 없이 조회되는지 실제 응답 검증이 필요하다.
         *
         * 검증 전까지 NOT_FOUND 결과만으로 즉시 실패시키지 않고
         * reconciliation 재시도 정책을 통해 제한적으로 재확인한다.
         */
        String orderNo =
                order.getBrokerOrderNo() == null
                ? ""
                : order.getBrokerOrderNo();

        KisOrderInquiryResponse response;
        try {
            response =
                    kisOrderClient.inquireDailyOrders(
                            "Bearer " + tokenResponse.accessToken(),
                            tokenResponse.appKey(),
                            tokenResponse.secretKey(),
                            KIS_VIRTUAL_INQUIRY_TR_ID,
                            CUSTOMER_TYPE,

                            infoResponse.cano(),
                            infoResponse.accountProductCode(),
                            orderDate,
                            orderDate,
                            sellBuyDivisionCode,
                            order.getStockCode(),
                            orderBranchNo,
                            orderNo,

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
                    "KIS 주문 조회 중 오류가 발생했습니다. orderId={}",
                    orderId,
                    e
            );

            orderStatusCommandService.recordReconciliationFailure(orderId);
            return;
        }

        if (!"0".equals(response.rtCd())) {
            log.warn(
                    "KIS 주문 조회 실패 응답입니다. orderId={}, msgCd={}, message={}",
                    orderId,
                    response.msgCd(),
                    response.message()
            );

            orderStatusCommandService.recordReconciliationFailure(orderId);
            return;
        }

        MatchResult matchResult =
                kisOrderMatcher.match(
                        order,
                        response.output1()
                );

        switch (matchResult.status()){
            case MATCHED ->
                reconcileMatchedOrder(
                        orderId,
                        matchResult.item()
                );

            case NOT_FOUND -> {
                log.warn(
                        "KIS 주문 조회 결과에서 일치하는 주문을 찾지 못했습니다. orderId={}",
                        orderId
                );

                orderStatusCommandService.recordReconciliationFailure(orderId);
            }

            case AMBIGUOUS -> {
                log.warn(
                        "KIS 주문 조회 결과가 여러 건 매칭되어 상태를 확정하지 못했습니다. orderId={}",
                        orderId
                );

                orderStatusCommandService.recordReconciliationFailure(orderId);
            }
        }

    }

    void reconcileMatchedOrder(
            UUID orderId,
            KisOrderInquiryItem item
    ){
        int orderQuantity;
        int rejectedQuantity;

        try {
            orderQuantity =
                    Integer.parseInt(item.orderQuantity());

            rejectedQuantity =
                    Integer.parseInt(item.rejectedQuantity());
        }
        catch (NumberFormatException | NullPointerException e) {
            log.warn(
                    "KIS 주문 수량 파싱 실패로 상태를 확정할 수 없습니다. orderId={}, orderNo={}",
                    orderId,
                    item.orderNo()
            );

            orderStatusCommandService.recordReconciliationFailure(orderId);
            return;
        }

        /*
         * 전체 주문 수량이 거절된 경우에만
         * 명확한 주문 실패로 판단한다.
         */

        if (orderQuantity > 0
                && rejectedQuantity == orderQuantity) {
            orderStatusCommandService.fail(
                    orderId,
                    "KIS_ORDER_REJECTED",
                    "KIS에서 주문이 거절되었습니다."
            );
            return;
        }

        /*
         * KIS에서 취소된 주문으로 확인된 경우
         * 주문번호가 존재한다는 이유만으로 ACCEPTED 처리하지 않는다.
         *
         * 체결이 일부 발생한 뒤 잔여 주문이 취소되는 경우도 있을 수 있으므로
         * 이번 이슈에서는 임의로 FAILED 처리하지 않고 상태 보정을 보류한다.
         */
        if ("Y".equalsIgnoreCase(item.canceled())) {
            log.warn(
                    "KIS에서 취소된 주문으로 확인되어 현재 이슈 범위에서는 상태를 확정하지 않습니다. orderId={}, orderNo={}",
                    orderId,
                    item.orderNo()
            );

            orderStatusCommandService.recordReconciliationFailure(orderId);
            return;
        }

        /*
         * 주문번호가 존재한다면 KIS에 주문이 접수된 것으로 판단한다.
         *
         * PROCESSING / TIMEOUT 상태를 ACCEPTED로 보정하며,
         * brokerOrderNo가 없었던 주문은 이 시점에 복구된다.
         */
        if (item.orderNo() != null
                && !item.orderNo().isBlank()) {

            orderStatusCommandService.accept(
                    orderId,
                    item.orderNo()
            );
            return;
        }

        /*
         * 전체 거절도 아니고 주문번호도 확인할 수 없다면
         * 조회 결과만으로 주문 상태를 확정할 수 없으므로
         * 기존 PROCESSING / TIMEOUT 상태를 유지한다.
         */
        log.warn(
                "KIS 주문 조회 결과만으로 상태를 확정할 수 없습니다. orderId={}",
                orderId
        );

        orderStatusCommandService.recordReconciliationFailure(orderId);
    }


}
