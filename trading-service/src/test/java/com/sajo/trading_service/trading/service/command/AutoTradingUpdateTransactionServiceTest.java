package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.controller.dto.request.AutoTradingUpdateRequest;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingUpdateResponse;
import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.domain.enums.AutoTradingDirection;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.command.AutoTradingCommandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AutoTradingUpdateTransactionServiceTest {

    @Mock
    private AutoTradingCommandRepository autoTradingCommandRepository;

    @InjectMocks
    private AutoTradingUpdateTransactionService autoTradingUpdateTransactionService;

    @Test
    @DisplayName("자동매매 활성 상태를 수정한다")
    void updateEnabled() {
        // given
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        AutoTrading autoTrading =
                AutoTrading.create(
                        userId,
                        strategyId,
                        AutoTradingDirection.BOTH
                );

        AutoTradingUpdateRequest request =
                new AutoTradingUpdateRequest(
                        true,
                        null
                );

        given(autoTradingCommandRepository
                .findByIdAndUserIdForUpdate(
                        autoTradingId,
                        userId
                ))
                .willReturn(Optional.of(autoTrading));

        // when
        AutoTradingUpdateResponse response =
                autoTradingUpdateTransactionService.update(
                        userId,
                        autoTradingId,
                        request
                );

        // then
        assertThat(autoTrading.getEnabled())
                .isTrue();

        assertThat(response.enabled())
                .isTrue();

        assertThat(response.direction())
                .isEqualTo(AutoTradingDirection.BOTH);
    }

    @Test
    @DisplayName("자동매매 주문 방향을 수정한다")
    void updateDirection() {
        // given
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        AutoTrading autoTrading =
                AutoTrading.create(
                        userId,
                        strategyId,
                        AutoTradingDirection.BOTH
                );

        AutoTradingUpdateRequest request =
                new AutoTradingUpdateRequest(
                        null,
                        AutoTradingDirection.SELL_ONLY
                );

        given(autoTradingCommandRepository
                .findByIdAndUserIdForUpdate(
                        autoTradingId,
                        userId
                ))
                .willReturn(Optional.of(autoTrading));

        // when
        AutoTradingUpdateResponse response =
                autoTradingUpdateTransactionService.update(
                        userId,
                        autoTradingId,
                        request
                );

        // then
        assertThat(autoTrading.getEnabled())
                .isFalse();

        assertThat(autoTrading.getDirection())
                .isEqualTo(AutoTradingDirection.SELL_ONLY);

        assertThat(response.direction())
                .isEqualTo(AutoTradingDirection.SELL_ONLY);
    }

    @Test
    @DisplayName("수정 시 자동매매 설정이 존재하지 않으면 예외가 발생한다")
    void updateAutoTradingNotFound() {
        // given
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();

        AutoTradingUpdateRequest request =
                new AutoTradingUpdateRequest(
                        true,
                        null
                );

        given(autoTradingCommandRepository
                .findByIdAndUserIdForUpdate(
                        autoTradingId,
                        userId
                ))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                autoTradingUpdateTransactionService.update(
                        userId,
                        autoTradingId,
                        request
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    TradingErrorCode.AUTO_TRADING_NOT_FOUND
                            );
                });
    }
}