package com.sajo.market_service.strategy.repository.command;

import com.sajo.market_service.strategy.domain.Backtest;
import com.sajo.market_service.strategy.domain.BacktestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface BacktestCommandRepository extends JpaRepository<Backtest, UUID> {
    List<Backtest> findByStatusAndUpdatedAtBefore(BacktestStatus status, Instant threshold);
}
