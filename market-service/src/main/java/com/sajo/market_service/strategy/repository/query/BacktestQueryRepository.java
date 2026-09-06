package com.sajo.market_service.strategy.repository.query;

import com.sajo.market_service.strategy.domain.Backtest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BacktestQueryRepository extends JpaRepository<Backtest, UUID> {

    Optional<Backtest> findByIdAndStrategyIdAndUserIdAndDeletedAtIsNull(
            UUID id,
            UUID strategyId,
            UUID userId
    );

    Page<Backtest> findByStrategyIdAndUserIdAndDeletedAtIsNull(
            UUID strategyId,
            UUID userId,
            Pageable pageable
    );

    Optional<Backtest> findByIdAndDeletedAtIsNull(UUID id);
}
