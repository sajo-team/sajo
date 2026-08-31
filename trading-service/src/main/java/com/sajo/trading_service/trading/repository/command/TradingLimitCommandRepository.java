package com.sajo.trading_service.trading.repository.command;

import com.sajo.trading_service.trading.domain.TradingLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TradingLimitCommandRepository extends JpaRepository<TradingLimit, UUID> {

    boolean existsByUserId(UUID userId);
}
