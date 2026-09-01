package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.controller.dto.request.TradingLimitCreateRequest;
import com.sajo.trading_service.trading.controller.dto.request.TradingLimitUpdateRequest;
import com.sajo.trading_service.trading.controller.dto.response.TradingLimitCreateResponse;
import com.sajo.trading_service.trading.controller.dto.response.TradingLimitUpdateResponse;
import com.sajo.trading_service.trading.domain.TradingLimit;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.command.TradingLimitCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TradingLimitCommandServiceTest {

    @Mock
    private TradingLimitCommandRepository tradingLimitCommandRepository;

    private TradingLimitCommandService tradingLimitCommandService;

    @BeforeEach
    void setUp() {
        tradingLimitCommandService =
                new TradingLimitCommandService(tradingLimitCommandRepository);
    }

    @Test
    @DisplayName("자동매매 공통 한도를 최초 설정할 수 있다")
    void createTradingLimit() {
        // given
        UUID userId = UUID.randomUUID();

        TradingLimitCreateRequest request = new TradingLimitCreateRequest(
                3_000_000L,
                10,
                new BigDecimal("5.00")
        );

        given(tradingLimitCommandRepository.existsByUserId(userId))
                .willReturn(false);

        given(tradingLimitCommandRepository.save(any(TradingLimit.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        TradingLimitCreateResponse response =
                tradingLimitCommandService.createTradingLimit(userId, request);

        // then
        ArgumentCaptor<TradingLimit> captor =
                ArgumentCaptor.forClass(TradingLimit.class);

        verify(tradingLimitCommandRepository).save(captor.capture());

        TradingLimit savedTradingLimit = captor.getValue();

        assertThat(savedTradingLimit.getUserId()).isEqualTo(userId);
        assertThat(savedTradingLimit.getDailyMaxOrderAmount()).isEqualTo(3_000_000L);
        assertThat(savedTradingLimit.getDailyMaxOrderCount()).isEqualTo(10);
        assertThat(savedTradingLimit.getDailyLossLimitRate())
                .isEqualByComparingTo("5.00");

        assertThat(response.dailyMaxOrderAmount()).isEqualTo(3_000_000L);
        assertThat(response.dailyMaxOrderCount()).isEqualTo(10);
        assertThat(response.dailyLossLimitRate())
                .isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("이미 공통 한도가 존재하면 생성할 수 없다")
    void createTradingLimitAlreadyExists() {
        // given
        UUID userId = UUID.randomUUID();

        TradingLimitCreateRequest request = new TradingLimitCreateRequest(
                3_000_000L,
                10,
                new BigDecimal("5.00")
        );

        given(tradingLimitCommandRepository.existsByUserId(userId))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() ->
                tradingLimitCommandService.createTradingLimit(userId, request)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(TradingErrorCode.TRADING_LIMIT_ALREADY_EXISTS);
                });

        verify(tradingLimitCommandRepository, never())
                .save(any(TradingLimit.class));
    }

    @Test
    @DisplayName("일일 최대 주문 금액이 0 이하이면 생성할 수 없다")
    void createTradingLimitInvalidOrderAmount() {
        // given
        UUID userId = UUID.randomUUID();

        TradingLimitCreateRequest request = new TradingLimitCreateRequest(
                0L,
                10,
                new BigDecimal("5.00")
        );

        given(tradingLimitCommandRepository.existsByUserId(userId))
                .willReturn(false);

        // when & then
        assertThatThrownBy(() ->
                tradingLimitCommandService.createTradingLimit(userId, request)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(TradingErrorCode.INVALID_TRADING_LIMIT);
                });

        verify(tradingLimitCommandRepository, never())
                .save(any(TradingLimit.class));
    }

    @Test
    @DisplayName("자동매매 공통 한도의 일부 항목만 수정한다")
    void updateTradingLimit() {
        // given
        UUID userId = UUID.randomUUID();

        TradingLimit tradingLimit = TradingLimit.create(
                userId,
                3_000_000L,
                10,
                new BigDecimal("5.00")
        );

        TradingLimitUpdateRequest request = new TradingLimitUpdateRequest(
                5_000_000L,
                null,
                null
        );

        given(tradingLimitCommandRepository.findByUserId(userId))
                .willReturn(Optional.of(tradingLimit));

        // when
        TradingLimitUpdateResponse response =
                tradingLimitCommandService.updateTradingLimit(userId, request);

        // then
        assertThat(response.dailyMaxOrderAmount()).isEqualTo(5_000_000L);
        assertThat(response.dailyMaxOrderCount()).isEqualTo(10);
        assertThat(response.dailyLossLimitRate())
                .isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("자동매매 공통 한도가 없으면 수정 시 예외가 발생한다")
    void updateTradingLimitNotFound() {
        // given
        UUID userId = UUID.randomUUID();

        TradingLimitUpdateRequest request = new TradingLimitUpdateRequest(
                5_000_000L,
                null,
                null
        );

        given(tradingLimitCommandRepository.findByUserId(userId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                tradingLimitCommandService.updateTradingLimit(userId, request)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    TradingErrorCode.TRADING_LIMIT_NOT_FOUND
                            );
                });
    }
}