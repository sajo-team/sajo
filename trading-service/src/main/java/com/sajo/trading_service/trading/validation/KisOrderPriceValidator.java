package com.sajo.trading_service.trading.validation;

import com.sajo.trading_service.trading.domain.enums.OrderType;
import org.springframework.stereotype.Component;

@Component
public class KisOrderPriceValidator {

    public boolean isValidTickSize(long orderPrice){
        if (orderPrice <= 0){
            return false;
        }

        long tickSize = resolveTickSize(orderPrice);

        return orderPrice % tickSize == 0;
    }

    /**
     * 신호가(signalPrice)를 해당 가격대의 호가단위(틱)에 맞도록 보정한다.
     *
     * <p>KIS 실시간 체결가(H0STCNT0)는 넥스트레이드(NXT) 출범 이후 KRX/NXT 통합 체결가를 내려주는데,
     * NXT는 일부 가격대에서 KRX보다 촘촘한 호가단위를 사용한다. 그 결과 신호가가 KRX 기준으로는 유효하지 않은 틱(예: 280,250원은 20만~50만원 구간 KRX 호가단위 500원의 배수가 아님)일 수 있다.
     * KIS 주문은 KRX 호가단위를 기준으로 접수되므로, 실제 주문 접수 전에 신호가를 유효 틱으로 스냅해서 거래소 거부를 방지한다.
     * 스냅 방향은 매매 방향에 따라 다르다 — 매수는 신호가보다 비싸게 사면 안 되므로 내림(하위 틱), 매도는 신호가보다 싸게 팔면 안 되므로
     * 올림(상위 틱)으로 보정해, 어느 방향으로 스냅하든 전략이 의도한 경제적 가치를 벗어나지 않도록 한다.</p>
     */
    public long snapToTickSize(long price, OrderType orderType) {
        if (price <= 0) {
            return price;
        }

        long tickSize = resolveTickSize(price);
        long remainder = price % tickSize;

        if (remainder == 0) {
            return price;
        }

        long roundedDown = price - remainder;
        long roundedUp = roundedDown + tickSize;

        return orderType == OrderType.BUY ? roundedDown : roundedUp;
    }

    public boolean isWithinDailyPriceLimit(
            long orderPrice,
            long previousClosePrice
    ) {
        if (orderPrice <= 0 || previousClosePrice <= 0) {
            return false;
        }

        long priceLimit = calculatePriceLimit(previousClosePrice);

        long lowerLimitPrice = previousClosePrice - priceLimit;
        long upperLimitPrice = previousClosePrice + priceLimit;

        return orderPrice >= lowerLimitPrice
                && orderPrice <= upperLimitPrice;
    }

    long resolveTickSize(long price) {
        if (price < 2_000) {
            return 1;
        }
        if (price < 5_000) {
            return 5;
        }

        if (price < 20_000) {
            return 10;
        }
        if (price < 50_000) {
            return 50;
        }
        if (price < 200_000) {
            return 100;
        }
        if (price < 500_000) {
            return 500;
        }

        return 1_000;
    }

    long calculatePriceLimit(long previousClosePrice) {
        long rawPriceLimit = previousClosePrice * 30 / 100;

        // KRX 기준에 따라 가격제한폭은 기준가격의 호가단위로 절사한다.
        // 상/하한가 결과값의 가격대 호가단위를 사용하는 것이 아니다.
        long tickSize = resolveTickSize(previousClosePrice);

        return rawPriceLimit / tickSize * tickSize;
    }
}
