package com.sajo.trading_service.trading.validation;

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
