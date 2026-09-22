package com.sajo.trading_service.trading.reconciliation;

import com.sajo.trading_service.trading.client.dto.response.KisOrderInquiryItem;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.validation.KisOrderPriceValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class KisOrderMatcher {

    private final KisOrderPriceValidator kisOrderPriceValidator;

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    /*
     * brokerOrderNo가 없는 TIMEOUT 주문은 주문 시각까지 조합해 매칭한다.
     *
     * 네트워크 지연 등을 고려해 내부 Order 생성 시각 기준
     * 앞뒤 5분 범위의 KIS 주문을 후보로 인정한다.
     */
    private static final long MATCH_TIME_RANGE_MINUTES = 5L;

    private static final DateTimeFormatter KIS_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final DateTimeFormatter KIS_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HHmmss");

    public MatchResult match(
            Order order,
            List<KisOrderInquiryItem> items
    ) {

        if (items == null || items.isEmpty()) {
            return MatchResult.notFound();
        }

        /*
         * 이미 brokerOrderNo를 알고 있다면
         * 다른 조건을 사용할 필요 없이 KIS 주문번호로 정확히 매칭한다.
         */
        if (order.getBrokerOrderNo() != null
                && !order.getBrokerOrderNo().isBlank()) {

            List<KisOrderInquiryItem> matched =
                    items.stream()
                            .filter(item ->
                                    order.getBrokerOrderNo()
                                            .equals(item.orderNo())
                            )
                            .toList();

            return resolve(matched);
        }

        /*
         * brokerOrderNo가 없는 경우에는
         * 종목 / 매수매도 / 수량 / 가격 / 주문 시각을 함께 비교한다.
         */
        List<KisOrderInquiryItem> matched =
                items.stream()
                        .filter(item -> matchesStockCode(order, item))
                        .filter(item -> matchesOrderType(order, item))
                        .filter(item -> matchesQuantity(order, item))
                        .filter(item -> matchesPrice(order, item))
                        .filter(item -> matchesOrderTime(order, item))
                        .toList();

        return resolve(matched);
    }

    private MatchResult resolve(List<KisOrderInquiryItem> matched) {

        if (matched.isEmpty()) {
            return MatchResult.notFound();
        }

        if (matched.size() > 1) {
            return MatchResult.ambiguous();
        }

        return MatchResult.matched(matched.getFirst());
    }

    private boolean matchesStockCode(
            Order order,
            KisOrderInquiryItem item
    ) {
        return order.getStockCode().equals(item.stockCode());
    }

    private boolean matchesOrderType(
            Order order,
            KisOrderInquiryItem item
    ) {

        /*
         * KIS:
         * 01 = 매도
         * 02 = 매수
         */
        String expectedCode =
                order.getOrderType() == OrderType.BUY
                        ? "02"
                        : "01";

        return expectedCode.equals(item.sellBuyDivisionCode());
    }

    private boolean matchesQuantity(
            Order order,
            KisOrderInquiryItem item
    ) {
        try {
            int quantity =
                    Integer.parseInt(item.orderQuantity());

            return order.getOrderQuantity() == quantity;

        } catch (NumberFormatException | NullPointerException e) {
            return false;
        }
    }

    private boolean matchesPrice(
            Order order,
            KisOrderInquiryItem item
    ) {
        try {
            long price =
                    Long.parseLong(item.orderPrice());

            /*
             * 실제 KIS 주문은 signalPrice가 아니라 호가단위(틱)로 스냅된 가격으로 접수된다(#315).
             * KIS가 돌려주는 체결 내역의 orderPrice도 그 스냅된 가격이므로, 매칭 기준 역시 동일해야
             * 한다. executedOrderPrice가 기록돼 있으면(주문 접수 시점에 실제로 넣은 값) 그 값을
             * 그대로 신뢰하고, 이 필드가 도입되기 전에 생성된 주문(null)만 signalPrice를 다시
             * 스냅해 하위 호환을 맞춘다.
             */
            long expectedPrice =
                    order.getExecutedOrderPrice() != null
                            ? order.getExecutedOrderPrice()
                            : kisOrderPriceValidator.snapToTickSize(order.getSignalPrice(), order.getOrderType());

            return expectedPrice == price;

        } catch (NumberFormatException | NullPointerException e) {
            return false;
        }
    }

    private boolean matchesOrderTime(
            Order order,
            KisOrderInquiryItem item
    ) {
        try {
            LocalDate date =
                    LocalDate.parse(
                            item.orderDate(),
                            KIS_DATE_FORMATTER
                    );

            LocalTime time =
                    LocalTime.parse(
                            item.orderTime(),
                            KIS_TIME_FORMATTER
                    );

            Instant kisOrderTime =
                    LocalDateTime.of(date, time)
                            .atZone(KOREA_ZONE)
                            .toInstant();

            Instant internalOrderTime =
                    order.getCreatedAt();

            Instant start =
                    internalOrderTime.minusSeconds(
                            MATCH_TIME_RANGE_MINUTES * 60
                    );

            Instant end =
                    internalOrderTime.plusSeconds(
                            MATCH_TIME_RANGE_MINUTES * 60
                    );

            return !kisOrderTime.isBefore(start)
                    && !kisOrderTime.isAfter(end);

        } catch (DateTimeParseException
                 | NullPointerException e) {
            return false;
        }
    }


}