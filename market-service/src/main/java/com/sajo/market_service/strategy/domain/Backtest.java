package com.sajo.market_service.strategy.domain;

import com.sajo.common.entity.BaseUpdatableEntity;
import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_strategy_backtests")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Backtest extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "strategy_id", nullable = false)
    private UUID strategyId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "stock_code", nullable = false, length = 6)
    private String stockCode;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "initial_cash", nullable = false)
    private Long initialCash;

    @Enumerated(EnumType.STRING)
    @Column(name = "backtest_status", nullable = false, length = 20)
    private BacktestStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "total_return_rate", precision = 10, scale = 4)
    private BigDecimal totalReturnRate;

    @Column(name = "mdd", precision = 10, scale = 4)
    private BigDecimal mdd;

    @Column(name = "win_rate", precision = 10, scale = 4)
    private BigDecimal winRate;

    @Column(name = "trade_count")
    private Integer tradeCount;

    @Column(name = "max_consecutive_losses")
    private Integer maxConsecutiveLosses;

    private Backtest(
            Strategy strategy,
            LocalDate startDate,
            LocalDate endDate,
            Long initialCash
    ) {
        this.strategyId = strategy.getId();
        this.userId = strategy.getUserId();
        this.stockCode = strategy.getStockCode();
        this.startDate = startDate;
        this.endDate = endDate;
        this.initialCash = initialCash;
        this.status = BacktestStatus.REQUESTED;
        this.requestedAt = Instant.now();
    }

    public static Backtest request(
            Strategy strategy,
            LocalDate startDate,
            LocalDate endDate,
            Long initialCash
    ) {
        if (strategy == null) {
            throw new BusinessException(StrategyErrorCode.STRATEGY_NOT_FOUND);
        }

        if (startDate == null || endDate == null) {
            throw new BusinessException(StrategyErrorCode.INVALID_STRATEGY, "백테스트 기간은 필수입니다.");
        }

        if (startDate.isAfter(endDate)) {
            throw new BusinessException(StrategyErrorCode.INVALID_STRATEGY, "시작일은 종료일보다 이후일 수 없습니다.");
        }

        if (initialCash == null || initialCash <= 0) {
            throw new BusinessException(StrategyErrorCode.INVALID_STRATEGY, "초기 투자 금액은 0보다 커야합니다.");
        }
        return new Backtest(strategy, startDate, endDate, initialCash);
    }

    public void start() {
        if (this.status != BacktestStatus.REQUESTED) {
            throw new BusinessException(StrategyErrorCode.INVALID_STRATEGY, "요청 상태의 백테스트만 실행할 수 있습니다.");
        }

        this.status = BacktestStatus.RUNNING;
    }

    public void complete(BigDecimal totalReturnRate, Integer tradeCount) {
        this.totalReturnRate = totalReturnRate;
        this.tradeCount = tradeCount;
        this.status = BacktestStatus.COMPLETED;
    }

    public void fail() {
        this.status = BacktestStatus.FAILED;
    }
}
