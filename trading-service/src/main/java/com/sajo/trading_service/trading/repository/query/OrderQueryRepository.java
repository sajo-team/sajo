package com.sajo.trading_service.trading.repository.query;

import com.sajo.trading_service.trading.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderQueryRepository extends JpaRepository<Order, UUID> {
    Page<Order> findByUserId(UUID userId, Pageable pageable);

    Optional<Order> findByIdAndUserId(UUID orderId, UUID userId);

    @Query("""
    select o.id
    from Order o
    where o.status = com.sajo.trading_service.trading.domain.enums.OrderStatus.REQUESTED
      and o.createdAt < :cutoff
      and o.deletedAt is null
    """)
    List<UUID> findStaleRequestedOrderIds(
            @Param("cutoff") Instant cutoff
    );

    @Query("""
    select o.id
    from Order o
    where o.status = com.sajo.trading_service.trading.domain.enums.OrderStatus.PROCESSING
      and o.updatedAt < :cutoff
      and o.deletedAt is null
    """)
    List<UUID> findStaleProcessingOrderIds(
            @Param("cutoff") Instant cutoff
    );
}
