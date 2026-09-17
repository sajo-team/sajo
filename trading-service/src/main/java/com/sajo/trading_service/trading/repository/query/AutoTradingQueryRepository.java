package com.sajo.trading_service.trading.repository.query;

import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.domain.enums.AutoTradingDirection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    boolean existsByUserIdAndEnabledTrueAndDeletedAtIsNull(
            UUID userId
    );

    @Query("""
        select a
        from AutoTrading a
        where a.deletedAt is null
          and (:userId is null or a.userId = :userId)
          and (:strategyId is null or a.strategyId = :strategyId)
          and (:direction is null or a.direction = :direction)
          and (:enabled is null or a.enabled = :enabled)
        order by a.updatedAt desc
    """)
    Page<AutoTrading> findAllForAdmin(
            @Param("userId") UUID userId,
            @Param("strategyId") UUID strategyId,
            @Param("direction") AutoTradingDirection direction,
            @Param("enabled") Boolean enabled,
            Pageable pageable
    );
}
