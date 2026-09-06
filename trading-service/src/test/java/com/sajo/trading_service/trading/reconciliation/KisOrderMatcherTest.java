package com.sajo.trading_service.trading.reconciliation;

import com.sajo.trading_service.trading.client.dto.response.KisOrderInquiryItem;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KisOrderMatcherTest {

    private final KisOrderMatcher kisOrderMatcher =
            new KisOrderMatcher();

    @Test
    @DisplayName("brokerOrderNo가 존재하면 KIS 주문번호로 매칭한다")
    void matchByBrokerOrderNo() {
        // given
        Order order = createOrder();

        ReflectionTestUtils.setField(
                order,
                "brokerOrderNo",
                "0001234567"
        );

        KisOrderInquiryItem item =
                createItem(
                        "0001234567",
                        "005930",
                        "02",
                        "7",
                        "69900",
                        "20260906",
                        "100000"
                );

        // when
        MatchResult result =
                kisOrderMatcher.match(
                        order,
                        List.of(item)
                );

        // then
        assertThat(result.status())
                .isEqualTo(MatchStatus.MATCHED);

        assertThat(result.item())
                .isEqualTo(item);
    }

    @Test
    @DisplayName("brokerOrderNo가 없으면 종목, 주문유형, 수량, 가격, 주문시간으로 매칭한다")
    void matchByOrderInformation() {
        // given
        Order order = createOrder();

        ReflectionTestUtils.setField(
                order,
                "createdAt",
                Instant.parse("2026-09-06T01:00:00Z")
        );

        /*
         * 내부 주문 생성 시각
         * 2026-09-06 10:00:00 KST
         *
         * KIS 주문 시각
         * 2026-09-06 10:02:00 KST
         *
         * → 5분 범위 안이므로 매칭
         */
        KisOrderInquiryItem item =
                createItem(
                        "0001234567",
                        "005930",
                        "02",
                        "7",
                        "69900",
                        "20260906",
                        "100200"
                );

        // when
        MatchResult result =
                kisOrderMatcher.match(
                        order,
                        List.of(item)
                );

        // then
        assertThat(result.status())
                .isEqualTo(MatchStatus.MATCHED);

        assertThat(result.item())
                .isEqualTo(item);
    }

    @Test
    @DisplayName("동일한 조건의 주문 후보가 여러 개이면 AMBIGUOUS를 반환한다")
    void matchAmbiguous() {
        // given
        Order order = createOrder();

        ReflectionTestUtils.setField(
                order,
                "createdAt",
                Instant.parse("2026-09-06T01:00:00Z")
        );

        KisOrderInquiryItem first =
                createItem(
                        "0001111111",
                        "005930",
                        "02",
                        "7",
                        "69900",
                        "20260906",
                        "100100"
                );

        KisOrderInquiryItem second =
                createItem(
                        "0002222222",
                        "005930",
                        "02",
                        "7",
                        "69900",
                        "20260906",
                        "100200"
                );

        // when
        MatchResult result =
                kisOrderMatcher.match(
                        order,
                        List.of(first, second)
                );

        // then
        assertThat(result.status())
                .isEqualTo(MatchStatus.AMBIGUOUS);

        assertThat(result.item())
                .isNull();
    }

    @Test
    @DisplayName("주문시간이 매칭 허용 범위를 벗어나면 NOT_FOUND를 반환한다")
    void matchNotFoundWhenOrderTimeOutsideRange() {
        // given
        Order order = createOrder();

        ReflectionTestUtils.setField(
                order,
                "createdAt",
                Instant.parse("2026-09-06T01:00:00Z")
        );

        /*
         * 내부 주문 생성 시각
         * 10:00 KST
         *
         * KIS 주문 시각
         * 10:10 KST
         *
         * → 허용 범위 ±5분을 벗어남
         */
        KisOrderInquiryItem item =
                createItem(
                        "0001234567",
                        "005930",
                        "02",
                        "7",
                        "69900",
                        "20260906",
                        "101000"
                );

        // when
        MatchResult result =
                kisOrderMatcher.match(
                        order,
                        List.of(item)
                );

        // then
        assertThat(result.status())
                .isEqualTo(MatchStatus.NOT_FOUND);

        assertThat(result.item())
                .isNull();
    }

    private Order createOrder() {
        return Order.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "005930",
                OrderType.BUY,
                69900L,
                7
        );
    }

    private KisOrderInquiryItem createItem(
            String orderNo,
            String stockCode,
            String sellBuyDivisionCode,
            String orderQuantity,
            String orderPrice,
            String orderDate,
            String orderTime
    ) {
        return new KisOrderInquiryItem(
                orderDate,
                "00000",
                orderNo,
                sellBuyDivisionCode,
                stockCode,
                orderQuantity,
                orderPrice,
                orderTime,
                "0",
                orderQuantity,
                "0",
                "N"
        );
    }
}