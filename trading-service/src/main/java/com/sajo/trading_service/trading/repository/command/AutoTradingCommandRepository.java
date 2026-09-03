package com.sajo.trading_service.trading.repository.command;

import com.sajo.trading_service.trading.domain.AutoTrading;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AutoTradingCommandRepository extends JpaRepository<AutoTrading, UUID> {
    boolean existsByUserIdAndStrategyIdAndDeletedAtIsNull(UUID userId, UUID strategyId);

    Optional<AutoTrading> findByIdAndUserIdAndDeletedAtIsNull(UUID autoTradingId, UUID userId);

    Optional<AutoTrading> findByUserIdAndStrategyIdAndDeletedAtIsNull(UUID userId, UUID strategyId);
}
