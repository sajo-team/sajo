package com.sajo.trading_service.trading.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class KisOrderPriceValidatorTest {

    private final KisOrderPriceValidator validator =
            new KisOrderPriceValidator();

    @ParameterizedTest
    @CsvSource({
            "1999, true",
            "2000, true",
            "2001, false",
            "4995, true",
            "5000, true",
            "5005, false",
            "19990, true",
            "20000, true",
            "20010, false",
            "20050, true",
            "50000, true",
            "50050, false",
            "70000, true",
            "70100, true",
            "200000, true",
            "200100, false",
            "200500, true",
            "500000, true",
            "500500, false",
            "501000, true"
    })
    @DisplayName("가격대별 호가단위에 맞는 주문 가격인지 검증한다")
    void validateTickSize(
            long price,
            boolean expected
    ) {
        assertThat(validator.isValidTickSize(price))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "0",
            "-1",
            "-1000"
    })
    @DisplayName("0 이하 주문 가격은 유효하지 않다")
    void invalidNonPositivePrice(long price) {
        assertThat(validator.isValidTickSize(price))
                .isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "6960, true",
            "12920, true",
            "6950, false",
            "12930, false",
            "10000, true"
    })
    @DisplayName("전일 종가 기준 당일 가격제한폭 안에 있는지 검증한다")
    void validateDailyPriceLimit(
            long orderPrice,
            boolean expected
    ) {
        long previousClosePrice = 9_940;

        assertThat(
                validator.isWithinDailyPriceLimit(
                        orderPrice,
                        previousClosePrice
                )
        ).isEqualTo(expected);
    }
}