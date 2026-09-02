package com.sajo.trading_service.trading.repository.query;

import com.sajo.trading_service.trading.domain.AutoTrading;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AutoTradingQueryRepository extends JpaRepository<AutoTrading, UUID> {

    Page<AutoTrading> findAllByUserIdAndDeletedAtIsNull(
            UUID userId,
            Pageable pageable
    );

    Optional<AutoTrading> findByIdAndUserIdAndDeletedAtIsNull(
            UUID autoTradingId,
            UUID userId
    );
}
