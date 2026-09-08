package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.feign.FeignApiException;
import com.sajo.trading_service.trading.client.StrategyClient;
import com.sajo.trading_service.trading.client.dto.response.StrategyClientResponse;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AutoTradingCommandServiceTest {

    @Mock
    private AutoTradingCommandRepository autoTradingCommandRepository;

    @Mock
    private TradingLimitCommandRepository tradingLimitCommandRepository;

    @Mock
    private StrategyClient strategyClient;

    @Mock
    private AutoTradingCreateTransactionService autoTradingCreateTransactionService;

    @InjectMocks
    private AutoTradingCommandService autoTradingCommandService;

    @Test
    @DisplayName("전략 검증에 성공하면 자동매매 생성 트랜잭션을 실행한다")
    void createAutoTrading() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        AutoTradingCreateRequest request =
                new AutoTradingCreateRequest(strategyId);

        AutoTrading autoTrading =
                AutoTrading.create(userId, strategyId);

        AutoTradingCreateResponse expectedResponse =
                AutoTradingCreateResponse.from(autoTrading);

        given(strategyClient.getStrategy(strategyId))
                .willReturn(
                        new StrategyClientResponse(
                                strategyId,
                                userId
                        )
                );

        given(autoTradingCreateTransactionService.create(
                userId,
                request
        )).willReturn(expectedResponse);

        // when
        AutoTradingCreateResponse response =
                autoTradingCommandService.createAutoTrading(
                        userId,
                        request
                );

        // then
        assertThat(response.strategyId())
                .isEqualTo(strategyId);

        assertThat(response.enabled())
                .isTrue();

        verify(autoTradingCreateTransactionService)
                .create(userId, request);
    }

    @Test
    @DisplayName("존재하지 않는 전략이면 자동매매 설정을 생성할 수 없다")
    void createAutoTradingStrategyNotFound() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        AutoTradingCreateRequest request =
                new AutoTradingCreateRequest(strategyId);

        given(strategyClient.getStrategy(strategyId))
                .willThrow(
                        new FeignApiException(
                                "STRATEGY_0002",
                                "전략을 찾을 수 없습니다.",
                                404
                        )
                );

        // when & then
        assertThatThrownBy(() ->
                autoTradingCommandService.createAutoTrading(
                        userId,
                        request
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    TradingErrorCode.STRATEGY_NOT_FOUND
                            );
                });

        verify(autoTradingCreateTransactionService, never())
                .create(userId, request);
    }

    @Test
    @DisplayName("다른 사용자의 전략으로 자동매매 설정을 생성할 수 없다")
    void createAutoTradingWithOtherUserStrategy() {
        // given
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        AutoTradingCreateRequest request =
                new AutoTradingCreateRequest(strategyId);

        given(strategyClient.getStrategy(strategyId))
                .willReturn(
                        new StrategyClientResponse(
                                strategyId,
                                otherUserId
                        )
                );

        // when & then
        assertThatThrownBy(() ->
                autoTradingCommandService.createAutoTrading(
                        userId,
                        request
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    TradingErrorCode.STRATEGY_NOT_FOUND
                            );
                });

        verify(autoTradingCreateTransactionService, never())
                .create(userId, request);
    }

    @Test
    @DisplayName("Strategy 조회 중 다른 FeignApiException은 그대로 전파한다")
    void createAutoTradingOtherFeignApiException() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        AutoTradingCreateRequest request =
                new AutoTradingCreateRequest(strategyId);

        FeignApiException exception =
                new FeignApiException(
                        "STRATEGY_9999",
                        "Market Service 오류",
                        500
                );

        given(strategyClient.getStrategy(strategyId))
                .willThrow(exception);

        // when & then
        assertThatThrownBy(() ->
                autoTradingCommandService.createAutoTrading(
                        userId,
                        request
                )
        )
                .isSameAs(exception);

        verify(autoTradingCreateTransactionService, never())
                .create(userId, request);
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