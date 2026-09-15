package com.sajo.trading_service.trading.domain;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.domain.enums.AutoTradingDirection;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutoTradingTest {

    @Test
    @DisplayName("자동매매 생성 시 기본 비활성 상태로 생성된다")
    void createAutoTradingDisabledByDefault() {
        // given & when
        AutoTrading autoTrading =
                AutoTrading.create(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        AutoTradingDirection.BOTH
                );

        // then
        assertThat(autoTrading.getEnabled())
                .isFalse();

        assertThat(autoTrading.getDirection())
                .isEqualTo(AutoTradingDirection.BOTH);
    }

    @Test
    @DisplayName("BUY_ONLY 설정은 BUY 주문을 허용한다")
    void buyOnlyAllowsBuy() {
        // given
        AutoTrading autoTrading =
                createAutoTrading(AutoTradingDirection.BUY_ONLY);

        // when & then
        assertThatCode(() ->
                autoTrading.validateDirection(OrderType.BUY)
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BUY_ONLY 설정은 SELL 주문을 허용하지 않는다")
    void buyOnlyRejectsSell() {
        // given
        AutoTrading autoTrading =
                createAutoTrading(AutoTradingDirection.BUY_ONLY);

        // when & then
        assertThatThrownBy(() ->
                autoTrading.validateDirection(OrderType.SELL)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    TradingErrorCode.AUTO_TRADING_DIRECTION_NOT_ALLOWED
                            );
                });
    }

    @Test
    @DisplayName("SELL_ONLY 설정은 SELL 주문을 허용한다")
    void sellOnlyAllowsSell() {
        // given
        AutoTrading autoTrading =
                createAutoTrading(AutoTradingDirection.SELL_ONLY);

        // when & then
        assertThatCode(() ->
                autoTrading.validateDirection(OrderType.SELL)
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SELL_ONLY 설정은 BUY 주문을 허용하지 않는다")
    void sellOnlyRejectsBuy() {
        // given
        AutoTrading autoTrading =
                createAutoTrading(AutoTradingDirection.SELL_ONLY);

        // when & then
        assertThatThrownBy(() ->
                autoTrading.validateDirection(OrderType.BUY)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    TradingErrorCode.AUTO_TRADING_DIRECTION_NOT_ALLOWED
                            );
                });
    }

    @Test
    @DisplayName("BOTH 설정은 BUY와 SELL 주문을 모두 허용한다")
    void bothAllowsBuyAndSell() {
        // given
        AutoTrading autoTrading =
                createAutoTrading(AutoTradingDirection.BOTH);

        // when & then
        assertThatCode(() ->
                autoTrading.validateDirection(OrderType.BUY)
        ).doesNotThrowAnyException();

        assertThatCode(() ->
                autoTrading.validateDirection(OrderType.SELL)
        ).doesNotThrowAnyException();
    }

    private AutoTrading createAutoTrading(
            AutoTradingDirection direction
    ) {
        return AutoTrading.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                direction
        );
    }
}