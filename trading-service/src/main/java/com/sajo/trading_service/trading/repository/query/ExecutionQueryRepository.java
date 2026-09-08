package com.sajo.trading_service.trading.repository.query;

import com.sajo.trading_service.trading.domain.Execution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ExecutionQueryRepository
        extends JpaRepository<Execution, UUID> {

    @Query("""
        select e
        from Execution e, Order o
        where e.orderId = o.id
          and o.userId = :userId
          and o.deletedAt is null
          and e.deletedAt is null
        order by e.updatedAt desc
    """)
    Page<Execution> findByUserId(
            @Param("userId") UUID userId,
            Pageable pageable
    );

    @Query("""
        select e
        from Execution e, Order o
        where e.id = :executionId
          and e.orderId = o.id
          and o.userId = :userId
          and o.deletedAt is null
          and e.deletedAt is null
    """)
    Optional<Execution> findByIdAndUserId(
            @Param("executionId") UUID executionId,
            @Param("userId") UUID userId
    );
}