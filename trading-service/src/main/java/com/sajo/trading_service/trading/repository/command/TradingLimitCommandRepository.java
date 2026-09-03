package com.sajo.trading_service.trading.repository.command;

import com.sajo.trading_service.trading.domain.TradingLimit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TradingLimitCommandRepository extends JpaRepository<TradingLimit, UUID> {

    boolean existsByUserId(UUID userId);

    Optional<TradingLimit> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select t
        from TradingLimit t
        where t.userId = :userId
        """)
    Optional<TradingLimit> findByUserIdForUpdate(
            @Param("userId") UUID userId
    );
}
