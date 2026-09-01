package com.sajo.trading_service.trading.repository.query;

import com.sajo.trading_service.trading.domain.TradingLimit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TradingLimitQueryRepository extends JpaRepository<TradingLimit, UUID> {

    Optional<TradingLimit> findByUserId(UUID userId);
}
