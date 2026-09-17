package com.sajo.trading_service.trading.repository.command;

import com.sajo.trading_service.trading.domain.AutoTradingOperationControl;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AutoTradingOperationControlCommandRepository
        extends JpaRepository<AutoTradingOperationControl, UUID> {

    Optional<AutoTradingOperationControl> findByIdAndDeletedAtIsNull(
            UUID id
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select c
    from AutoTradingOperationControl c
    where c.id = :id
      and c.deletedAt is null
    """)
    Optional<AutoTradingOperationControl> findByIdForUpdate(
            @Param("id") UUID id
    );
}