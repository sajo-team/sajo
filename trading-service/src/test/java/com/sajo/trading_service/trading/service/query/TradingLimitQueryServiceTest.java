package com.sajo.trading_service.trading.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.controller.dto.response.TradingLimitQueryResponse;
import com.sajo.trading_service.trading.domain.TradingLimit;
import com.sajo.trading_service.trading.repository.query.TradingLimitQueryRepository;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TradingLimitQueryServiceTest {

    @Mock
    private TradingLimitQueryRepository tradingLimitQueryRepository;

    @InjectMocks
    private TradingLimitQueryService tradingLimitQueryService;

    @Test
    @DisplayName("사용자의 거래 한도를 조회한다")
    void findByUserId() {
        UUID userId = UUID.randomUUID();

        TradingLimit tradingLimit = TradingLimit.create(
                userId,
                3_000_000L,
                10,
                new BigDecimal("5.00")
        );

        given(tradingLimitQueryRepository.findByUserId(userId))
                .willReturn(Optional.of(tradingLimit));

        TradingLimitQueryResponse response =
                tradingLimitQueryService.findByUserId(userId);

        assertThat(response.dailyMaxOrderAmount()).isEqualTo(3_000_000L);
        assertThat(response.dailyMaxOrderCount()).isEqualTo(10);
        assertThat(response.dailyLossLimitRate())
                .isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("거래 한도가 존재하지 않으면 예외가 발생한다")
    void findByUserIdNotFound() {
        UUID userId = UUID.randomUUID();

        given(tradingLimitQueryRepository.findByUserId(userId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                tradingLimitQueryService.findByUserId(userId)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(TradingErrorCode.TRADING_LIMIT_NOT_FOUND);
                });
    }
}