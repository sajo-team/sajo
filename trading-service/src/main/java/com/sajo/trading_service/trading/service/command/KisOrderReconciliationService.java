package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.client.AccountClient;
import com.sajo.trading_service.trading.client.KisOrderClient;
import com.sajo.trading_service.trading.client.dto.response.AccountOrderInfoResponse;
import com.sajo.trading_service.trading.client.dto.response.AccountTokenResponse;
import com.sajo.trading_service.trading.client.dto.response.KisOrderInquiryItem;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.reconciliation.KisOrderMatcher;
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
        AccountTokenResponse tokenResponse =
                accountClient.getAccessToken(order.getUserId());

        AccountOrderInfoResponse infoResponse =
                accountClient.getOrderInfo(order.getUserId());

        /*
         * KIS 일별주문체결조회는 날짜 기준으로 조회한다.
         *
         * 현재는 내부 Order 생성일 기준 날짜를 사용한다.
         */

        String orderDate =
                order.getCreatedAt()
                        .atZone(ZoneId.of("Asia/Seoul"))
                        .format(DATE_FORMATTER);

        /*
         * TODO:
         * ORD_GNO_BRNO(주문채번지점번호)의 획득 방법 확인 필요.
         * KIS 문서상 Required=Y이나 현재 Account Service에서는 제공하지 않는다.
         */

        String orderBranchNo = null;

        /*
         * TODO:
         * ORD_GNO_BRNO 값 확정 후 KisOrderClient.inquireDailyOrders() 호출.
         *
         * 이후 구현 순서:
         *
         * 1. KIS 주문 목록 조회
         * 2. 내부 Order와 조회 결과 매칭
         * 3. 정확히 일치하는 주문이 있으면 ACCEPTED / FAILED 보정
         * 4. brokerOrderNo가 없다면 KIS 주문번호 복구
         * 5. 결과가 불확실하면 기존 상태 유지
         *
         * KIS 조회 호출은 ORD_GNO_BRNO 확인 후
         */

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
        catch (NumberFormatException | NullPointerException e){
            log.warn(
                    "KIS 주문 수량 파싱 실패로 상태 보정을 중단합니다. orderId={}, orderNo={}",
                    orderId,
                    item.orderNo()
            );
            return;
        }

        /*
         * 전체 주문 수량이 거절된 경우에만
         * 명확한 주문 실패로 판단한다.
         */

        if (rejectedQuantity == orderQuantity) {
            orderStatusCommandService.fail(
                    orderId,
                    "KIS_ORDER_REJECTED",
                    "KIS에서 주문이 거절되었습니다."
            );
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
    }


}
