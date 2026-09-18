package com.sajo.trading_service.trading.repository.query;

import com.sajo.trading_service.trading.domain.AutoTradingOperationControl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AutoTradingOperationControlQueryRepository
        extends JpaRepository<AutoTradingOperationControl, UUID> {

    Optional<AutoTradingOperationControl> findByIdAndDeletedAtIsNull(
            UUID id
    );
}