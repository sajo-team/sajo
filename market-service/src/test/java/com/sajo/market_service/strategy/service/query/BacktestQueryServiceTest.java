package com.sajo.market_service.strategy.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.strategy.controller.dto.response.BacktestDetailResponse;
import com.sajo.market_service.strategy.controller.dto.response.BacktestInternalResponse;
import com.sajo.market_service.strategy.controller.dto.response.BacktestListResponse;
import com.sajo.market_service.strategy.controller.dto.response.BacktestStatusResponse;
import com.sajo.market_service.strategy.domain.Backtest;
import com.sajo.market_service.strategy.domain.BacktestStatus;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import com.sajo.market_service.strategy.repository.query.BacktestQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BacktestQueryServiceTest {

    @Mock
    private BacktestQueryRepository backtestQueryRepository;

    private BacktestQueryService backtestQueryService;

    @BeforeEach
    void setUp() {
        backtestQueryService = new BacktestQueryService(backtestQueryRepository);
    }

    @Test
    @DisplayName("백테스트 상태를 조회하면 상태 응답을 반환한다")
    void getBacktestStatus() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();
        Backtest backtest = newBacktest(userId, strategyId, backtestId);

        given(backtestQueryRepository.findByIdAndStrategyIdAndUserIdAndDeletedAtIsNull(
                backtestId,
                strategyId,
                userId
        )).willReturn(Optional.of(backtest));

        // when
        BacktestStatusResponse response = backtestQueryService.getBacktestStatus(userId, strategyId, backtestId);

        // then
        assertThat(response.backtestId()).isEqualTo(backtestId);
        assertThat(response.status()).isEqualTo(BacktestStatus.REQUESTED);
    }

    @Test
    @DisplayName("백테스트 상세를 조회하면 결과 필드를 포함한 상세 응답을 반환한다")
    void getBacktestDetail() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();
        Backtest backtest = newCompletedBacktest(userId, strategyId, backtestId);

        given(backtestQueryRepository.findByIdAndStrategyIdAndUserIdAndDeletedAtIsNull(
                backtestId,
                strategyId,
                userId
        )).willReturn(Optional.of(backtest));

        // when
        BacktestDetailResponse response = backtestQueryService.getBacktestDetail(userId, strategyId, backtestId);

        // then
        assertThat(response.backtestId()).isEqualTo(backtestId);
        assertThat(response.strategyId()).isEqualTo(strategyId);
        assertThat(response.stockCode()).isEqualTo("005930");
        assertThat(response.status()).isEqualTo(BacktestStatus.COMPLETED);
        assertThat(response.totalReturnRate()).isEqualByComparingTo("8.2500");
        assertThat(response.mdd()).isEqualByComparingTo("-12.4000");
        assertThat(response.winRate()).isEqualByComparingTo("63.5000");
        assertThat(response.tradeCount()).isEqualTo(14);
        assertThat(response.maxConsecutiveLosses()).isEqualTo(3);
    }

    @Test
    @DisplayName("백테스트 목록을 조회하면 페이지 정보와 요약 목록을 반환한다")
    void getBacktests() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Backtest backtest = newBacktest(userId, strategyId, backtestId);

        given(backtestQueryRepository.findByStrategyIdAndUserIdAndDeletedAtIsNull(strategyId, userId, pageable))
                .willReturn(new PageImpl<>(List.of(backtest), pageable, 1));

        // when
        BacktestListResponse response = backtestQueryService.getBacktests(userId, strategyId, pageable);

        // then
        assertThat(response.backtests()).hasSize(1);
        assertThat(response.backtests().get(0).backtestId()).isEqualTo(backtestId);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("내부 백테스트 조회는 사용자 ID를 포함한 응답을 반환한다")
    void getBacktestInternal() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();
        Backtest backtest = newCompletedBacktest(userId, strategyId, backtestId);

        given(backtestQueryRepository.findByIdAndDeletedAtIsNull(backtestId))
                .willReturn(Optional.of(backtest));

        // when
        BacktestInternalResponse response = backtestQueryService.getBacktestInternal(backtestId);

        // then
        assertThat(response.backtestId()).isEqualTo(backtestId);
        assertThat(response.strategyId()).isEqualTo(strategyId);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.stockCode()).isEqualTo("005930");
        assertThat(response.totalReturnRate()).isEqualByComparingTo("8.2500");
        assertThat(response.mdd()).isEqualByComparingTo("-12.4000");
        assertThat(response.winRate()).isEqualByComparingTo("63.5000");
        assertThat(response.tradeCount()).isEqualTo(14);
        assertThat(response.maxConsecutiveLosses()).isEqualTo(3);
    }

    @Test
    @DisplayName("소유자 조건에 맞는 백테스트가 없으면 예외가 발생한다")
    void getBacktestDetailNotFound() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();

        given(backtestQueryRepository.findByIdAndStrategyIdAndUserIdAndDeletedAtIsNull(
                backtestId,
                strategyId,
                userId
        )).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> backtestQueryService.getBacktestDetail(userId, strategyId, backtestId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(StrategyErrorCode.BACKTEST_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("백테스트 목록 조회 조건을 리포지토리에 그대로 전달한다")
    void getBacktestsPassesConditionToRepository() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(1, 5);

        given(backtestQueryRepository.findByStrategyIdAndUserIdAndDeletedAtIsNull(strategyId, userId, pageable))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        // when
        backtestQueryService.getBacktests(userId, strategyId, pageable);

        // then
        verify(backtestQueryRepository).findByStrategyIdAndUserIdAndDeletedAtIsNull(strategyId, userId, pageable);
    }

    private Backtest newBacktest(UUID userId, UUID strategyId, UUID backtestId) {
        Strategy strategy = newStrategy(userId);
        ReflectionTestUtils.setField(strategy, "id", strategyId);

        Backtest backtest = Backtest.request(
                strategy,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                1_000_000L
        );
        ReflectionTestUtils.setField(backtest, "id", backtestId);

        return backtest;
    }

    private Backtest newCompletedBacktest(UUID userId, UUID strategyId, UUID backtestId) {
        Backtest backtest = newBacktest(userId, strategyId, backtestId);

        ReflectionTestUtils.setField(backtest, "status", BacktestStatus.COMPLETED);
        ReflectionTestUtils.setField(backtest, "totalReturnRate", new BigDecimal("8.2500"));
        ReflectionTestUtils.setField(backtest, "mdd", new BigDecimal("-12.4000"));
        ReflectionTestUtils.setField(backtest, "winRate", new BigDecimal("63.5000"));
        ReflectionTestUtils.setField(backtest, "tradeCount", 14);
        ReflectionTestUtils.setField(backtest, "maxConsecutiveLosses", 3);

        return backtest;
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
                null,
                null,
                null
        );
    }
}
