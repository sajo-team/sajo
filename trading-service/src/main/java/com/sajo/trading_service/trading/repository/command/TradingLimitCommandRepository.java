package com.sajo.trading_service.trading.repository.command;

import com.sajo.trading_service.trading.domain.TradingLimit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TradingLimitCommandRepository extends JpaRepository<TradingLimit, UUID> {

    boolean existsByUserId(UUID userId);
    Optional<TradingLimit> findByUserId(UUID userId);
}
