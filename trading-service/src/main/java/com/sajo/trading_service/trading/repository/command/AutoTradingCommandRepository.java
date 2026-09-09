package com.sajo.trading_service.trading.repository.command;

import com.sajo.trading_service.trading.domain.AutoTrading;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AutoTradingCommandRepository extends JpaRepository<AutoTrading, UUID> {
    boolean existsByUserIdAndStrategyIdAndDeletedAtIsNull(UUID userId, UUID strategyId);

    Optional<AutoTrading> findByIdAndUserIdAndDeletedAtIsNull(UUID autoTradingId, UUID userId);

    Optional<AutoTrading> findByUserIdAndStrategyIdAndDeletedAtIsNull(UUID userId, UUID strategyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select a
    from AutoTrading a
    where a.id = :autoTradingId
      and a.userId = :userId
      and a.deletedAt is null
    """)
    Optional<AutoTrading> findByIdAndUserIdForUpdate(
            @Param("autoTradingId") UUID autoTradingId,
            @Param("userId") UUID userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select a
    from AutoTrading a
    where a.userId = :userId
      and a.strategyId = :strategyId
      and a.deletedAt is null
    """)
    Optional<AutoTrading> findByUserIdAndStrategyIdForUpdate(
            @Param("userId") UUID userId,
            @Param("strategyId") UUID strategyId
    );
}
