package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.controller.dto.request.AutoTradingCreateRequest;
import com.sajo.trading_service.trading.controller.dto.request.AutoTradingUpdateRequest;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingCreateResponse;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingUpdateResponse;
import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.command.AutoTradingCommandRepository;
import com.sajo.trading_service.trading.repository.command.TradingLimitCommandRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AutoTradingCommandServiceTest {

    @Mock
    private AutoTradingCommandRepository autoTradingCommandRepository;

    @Mock
    private TradingLimitCommandRepository tradingLimitCommandRepository;

    @InjectMocks
    private AutoTradingCommandService autoTradingCommandService;

    @Test
    @DisplayName("자동매매 설정을 생성하면 enabled는 true이다")
    void createAutoTrading() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        AutoTradingCreateRequest request =
                new AutoTradingCreateRequest(strategyId);

        given(tradingLimitCommandRepository.existsByUserId(userId))
                .willReturn(true);

        given(autoTradingCommandRepository
                .existsByUserIdAndStrategyIdAndDeletedAtIsNull(
                        userId,
                        strategyId
                ))
                .willReturn(false);

        given(autoTradingCommandRepository.save(any(AutoTrading.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        AutoTradingCreateResponse response =
                autoTradingCommandService.createAutoTrading(userId, request);

        // then
        assertThat(response.strategyId()).isEqualTo(strategyId);
        assertThat(response.enabled()).isTrue();

        verify(autoTradingCommandRepository)
                .save(any(AutoTrading.class));
    }

    @Test
    @DisplayName("자동매매 공통 한도가 없으면 자동매매 설정을 생성할 수 없다")
    void createAutoTradingWithoutTradingLimit() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        AutoTradingCreateRequest request =
                new AutoTradingCreateRequest(strategyId);

        given(tradingLimitCommandRepository.existsByUserId(userId))
                .willReturn(false);

        // when & then
        assertThatThrownBy(() ->
                autoTradingCommandService.createAutoTrading(userId, request)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    TradingErrorCode.TRADING_LIMIT_REQUIRED
                            );
                });

        verify(autoTradingCommandRepository, never())
                .save(any(AutoTrading.class));
    }

    @Test
    @DisplayName("동일 전략의 자동매매 설정이 이미 존재하면 생성할 수 없다")
    void createAutoTradingAlreadyExists() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        AutoTradingCreateRequest request =
                new AutoTradingCreateRequest(strategyId);

        given(tradingLimitCommandRepository.existsByUserId(userId))
                .willReturn(true);

        given(autoTradingCommandRepository
                .existsByUserIdAndStrategyIdAndDeletedAtIsNull(
                        userId,
                        strategyId
                ))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() ->
                autoTradingCommandService.createAutoTrading(userId, request)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    TradingErrorCode.AUTO_TRADING_ALREADY_EXISTS
                            );
                });

        verify(autoTradingCommandRepository, never())
                .save(any(AutoTrading.class));
    }

    @Test
    @DisplayName("자동매매 설정의 활성 상태를 수정한다")
    void updateAutoTrading() {
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        AutoTrading autoTrading =
                AutoTrading.create(userId, strategyId);

        AutoTradingUpdateRequest request =
                new AutoTradingUpdateRequest(false);

        given(autoTradingCommandRepository
                .findByIdAndUserIdAndDeletedAtIsNull(
                        autoTradingId,
                        userId
                ))
                .willReturn(Optional.of(autoTrading));

        AutoTradingUpdateResponse response =
                autoTradingCommandService.updateAutoTrading(
                        userId,
                        autoTradingId,
                        request
                );

        assertThat(response.enabled()).isFalse();
        assertThat(response.strategyId()).isEqualTo(strategyId);
    }

    @Test
    @DisplayName("자동매매 설정이 없으면 수정 시 예외가 발생한다")
    void updateAutoTradingNotFound() {
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();

        AutoTradingUpdateRequest request =
                new AutoTradingUpdateRequest(false);

        given(autoTradingCommandRepository
                .findByIdAndUserIdAndDeletedAtIsNull(
                        autoTradingId,
                        userId
                ))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                autoTradingCommandService.updateAutoTrading(
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

    @Test
    @DisplayName("자동매매 활성화 시 공통 한도가 없으면 예외가 발생한다")
    void updateAutoTradingWithoutTradingLimit() {
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        AutoTrading autoTrading =
                AutoTrading.create(userId, strategyId);

        AutoTradingUpdateRequest request =
                new AutoTradingUpdateRequest(true);

        given(autoTradingCommandRepository
                .findByIdAndUserIdAndDeletedAtIsNull(
                        autoTradingId,
                        userId
                ))
                .willReturn(Optional.of(autoTrading));

        given(tradingLimitCommandRepository.existsByUserId(userId))
                .willReturn(false);

        assertThatThrownBy(() ->
                autoTradingCommandService.updateAutoTrading(
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
                                    TradingErrorCode.TRADING_LIMIT_REQUIRED
                            );
                });
    }
}