package com.sajo.market_service.strategy.repository.command;

import com.sajo.market_service.strategy.domain.Backtest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BacktestCommandRepository extends JpaRepository<Backtest, UUID> {
}
