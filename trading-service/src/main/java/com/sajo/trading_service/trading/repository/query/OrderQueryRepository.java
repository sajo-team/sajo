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
  and o.deletedAt is null
  and (
        (o.accountRetryCount = 0 and o.updatedAt < :normalCutoff)
        or
        (o.accountRetryCount > 0 and o.updatedAt < :retryCutoff)
      )
""")
    List<UUID> findStaleRequestedOrderIds(
            @Param("normalCutoff") Instant normalCutoff,
            @Param("retryCutoff") Instant retryCutoff
    );

    @Query("""
    select o.id
    from Order o
    where o.status = com.sajo.trading_service.trading.domain.enums.OrderStatus.PROCESSING
      and o.updatedAt <  :cutoff
      and o.deletedAt is null
    """)
    List<UUID> findStaleProcessingOrderIds(
            @Param("cutoff") Instant cutoff
    );

    @Query("""
    select o.id
    from Order o
    where o.status = com.sajo.trading_service.trading.domain.enums.OrderStatus.TIMEOUT
      and o.updatedAt < :cutoff
      and o.deletedAt is null
    """)
    List<UUID> findStaleTimeoutOrderIds(
            @Param("cutoff") Instant cutoff
    );

    @Query("""
    select o.id
    from Order o
    where o.status in (
        com.sajo.trading_service.trading.domain.enums.OrderStatus.ACCEPTED,
        com.sajo.trading_service.trading.domain.enums.OrderStatus.PARTIALLY_FILLED
    )
      and (
          o.lastExecutionCheckedAt is null
          or o.lastExecutionCheckedAt < :cutoff
      )
      and o.deletedAt is null
    """)
    List<UUID> findExecutionTargetOrderIds(
            @Param("cutoff") Instant cutoff
    );

    Optional<Order> findByIdAndDeletedAtIsNull(UUID orderId);

    @Query("""
    select case when count(o) > 0 then true else false end
    from Order o
    where o.userId = :userId
      and o.deletedAt is null
      and o.status in (
          com.sajo.trading_service.trading.domain.enums.OrderStatus.REQUESTED,
          com.sajo.trading_service.trading.domain.enums.OrderStatus.PROCESSING,
          com.sajo.trading_service.trading.domain.enums.OrderStatus.TIMEOUT,
          com.sajo.trading_service.trading.domain.enums.OrderStatus.ACCEPTED,
          com.sajo.trading_service.trading.domain.enums.OrderStatus.PARTIALLY_FILLED
      )
    """)
    boolean existsActiveOrderByUserId(
            @Param("userId") UUID userId
    );

    boolean existsByBrokerOrderNoAndIdNotAndDeletedAtIsNull(
            String brokerOrderNo,
            UUID orderId
    );

    @Query(value = """
    SELECT EXISTS (
        SELECT 1
        FROM trading.p_orders o
        WHERE o.auto_trading_id = :autoTradingId
          AND o.deleted_at IS NULL
        GROUP BY o.stock_code
        HAVING SUM(
            CASE
                WHEN o.order_type = 'BUY' THEN o.filled_quantity
                WHEN o.order_type = 'SELL' THEN -o.filled_quantity
                ELSE 0
            END
        ) > 0
    )
    """, nativeQuery = true)
    boolean existsOpenPositionByAutoTradingId(
            @Param("autoTradingId") UUID autoTradingId
    );

    @Query("""
    select case when count(o) > 0 then true else false end
    from Order o
    where o.autoTradingId = :autoTradingId
      and o.deletedAt is null
      and o.status in (
          com.sajo.trading_service.trading.domain.enums.OrderStatus.REQUESTED,
          com.sajo.trading_service.trading.domain.enums.OrderStatus.PROCESSING,
          com.sajo.trading_service.trading.domain.enums.OrderStatus.TIMEOUT,
          com.sajo.trading_service.trading.domain.enums.OrderStatus.ACCEPTED,
          com.sajo.trading_service.trading.domain.enums.OrderStatus.PARTIALLY_FILLED
      )
    """)
    boolean existsActiveOrderByAutoTradingId(
            @Param("autoTradingId") UUID autoTradingId
    );

}
