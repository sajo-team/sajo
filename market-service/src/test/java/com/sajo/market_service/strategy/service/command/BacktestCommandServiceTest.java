package com.sajo.market_service.strategy.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.strategy.controller.dto.request.BacktestCreateRequest;
import com.sajo.market_service.strategy.controller.dto.response.BacktestCreateResponse;
import com.sajo.market_service.strategy.domain.Backtest;
import com.sajo.market_service.strategy.domain.BacktestStatus;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import com.sajo.market_service.strategy.repository.command.BacktestCommandRepository;
import com.sajo.market_service.strategy.repository.command.StrategyCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BacktestCommandServiceTest {

    @Mock
    private StrategyCommandRepository strategyCommandRepository;

    @Mock
    private BacktestCommandRepository backtestCommandRepository;

    @Mock
    private BacktestExecutionService backtestExecutionService;

    private BacktestCommandService backtestCommandService;

    @BeforeEach
    void setUp() {
        backtestCommandService = new BacktestCommandService(
                strategyCommandRepository,
                backtestCommandRepository,
                backtestExecutionService
        );
    }

    @Test
    @DisplayName("백테스트 실행이 완료되면 최신 상태를 조회해 응답한다.")
    void createBacktest() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();
        AtomicReference<Backtest> savedReference = new AtomicReference<>();

        Strategy strategy = newStrategy(userId);
        ReflectionTestUtils.setField(strategy, "id", strategyId);

        BacktestCreateRequest request = new BacktestCreateRequest(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                1_000_000L
        );

        given(strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId))
                .willReturn(Optional.of(strategy));

        given(backtestCommandRepository.saveAndFlush(any(Backtest.class)))
                .willAnswer(invocation -> {
                    Backtest backtest = invocation.getArgument(0);
                    ReflectionTestUtils.setField(backtest, "id", backtestId);
                    savedReference.set(backtest);
                    return backtest;
                });

        // 동기 실행 자체는 BacktestExecutionServiceTest에서 검증한다.
        // 이 테스트에서는 생성 서비스가 실행 서비스를 연결하는지만 확인한다.
        org.mockito.BDDMockito.willDoNothing()
                .given(backtestExecutionService)
                .execute(backtestId);

        given(backtestCommandRepository.findById(backtestId))
                .willAnswer(invocation -> {
                    Backtest executedBacktest = savedReference.get();
                    executedBacktest.start();
                    executedBacktest.complete(BigDecimal.ZERO, 0);
                    return Optional.of(executedBacktest);
                });

        // when
        BacktestCreateResponse response = backtestCommandService.createBacktest(userId, strategyId, request);

        // then
        ArgumentCaptor<Backtest> captor = ArgumentCaptor.forClass(Backtest.class);
        verify(backtestCommandRepository).saveAndFlush(captor.capture());
        verify(backtestExecutionService).execute(backtestId);

        Backtest savedBacktest = captor.getValue();
        assertThat(savedBacktest.getStrategyId()).isEqualTo(strategyId);
        assertThat(savedBacktest.getUserId()).isEqualTo(userId);
        assertThat(savedBacktest.getStockCode()).isEqualTo("005930");
        assertThat(savedBacktest.getStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(savedBacktest.getEndDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(savedBacktest.getInitialCash()).isEqualTo(1_000_000L);
        assertThat(savedBacktest.getStatus()).isEqualTo(BacktestStatus.COMPLETED);
        assertThat(savedBacktest.getRequestedAt()).isNotNull();

        assertThat(response.backtestId()).isEqualTo(backtestId);
        assertThat(response.strategyId()).isEqualTo(strategyId);
        assertThat(response.status()).isEqualTo(BacktestStatus.COMPLETED);
        assertThat(response.requestedAt()).isNotNull();
    }

    @Test
    @DisplayName("백테스트를 요청할 전략이 없으면 예외가 발생한다.")
    void createBacktestStrategyNotFound() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        BacktestCreateRequest request = new BacktestCreateRequest(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                 1_000_000L
        );

        given(strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> backtestCommandService.createBacktest(userId, strategyId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(StrategyErrorCode.STRATEGY_NOT_FOUND);
                });
        verify(backtestCommandRepository, never()).save(any(Backtest.class));
    }

    @Test
    @DisplayName("백테스트 시작일이 종료일보다 이후이면 예외가 발생한다.")
    void createBacktestInvalidPeriod() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        Strategy strategy = newStrategy(userId);
        ReflectionTestUtils.setField(strategy, "id", strategyId);

        BacktestCreateRequest request = new BacktestCreateRequest(
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 3, 30),
                  1_000_000L
        );

        given(strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId))
                .willReturn(Optional.of(strategy));

        // when & then
        assertThatThrownBy(() -> backtestCommandService.createBacktest(userId, strategyId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(StrategyErrorCode.INVALID_STRATEGY);
                });

        verify(backtestCommandRepository, never()).save(any(Backtest.class));
    }

    private Strategy newStrategy(UUID userId) {
        return Strategy.create(
                userId,
                UUID.randomUUID(),
                "005930",
                "삼성전자 눌림목 전략",
                70_000L,
                80_000L,
                new BigDecimal("5.0000"),
                null,
                3_000_000L,
                100_000L,
                null,
                null,
                null
        );
    }
}
