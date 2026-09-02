package com.sajo.market_service.strategy.repository.query;

import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.domain.StrategyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StrategyQueryRepository extends JpaRepository<Strategy, UUID> {

    @Query("""
        select s
        from Strategy s
        where s.userId = :userId
            and s.deletedAt is null
            and (:status is null or s.status = :status) 
            and (:stockCode is null or s.stockCode = :stockCode)
    """)
    Page<Strategy> findStrategies(
            UUID userId,
            StrategyStatus status,
            String stockCode,
            Pageable pageable
    );

    Optional<Strategy> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
