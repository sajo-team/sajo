package com.sajo.trading_service.trading.repository.command;

import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OrderCommandRepository extends JpaRepository<Order, UUID> {
    boolean existsBySignalId(UUID signalId);

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

    long countByAutoTradingIdAndDeletedAtIsNull(
            UUID autoTradingId
    );

    @Query("""
    select count(o)
    from Order o
    where o.userId = :userId
      and o.status <> :excludedStatus
      and o.createdAt >= :start
      and o.createdAt < :end
    """)
    long countOrdersByUserIdAndCreatedAtBetween(
            @Param("userId") UUID userId,
            @Param("excludedStatus") OrderStatus excludedStatus,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    @Query("""
        select coalesce(sum(o.estimatedOrderAmount), 0)
        from Order o
        where o.userId = :userId
          and o.status <> :excludedStatus
          and o.createdAt >= :start
          and o.createdAt < :end
        """)
    Long sumEstimatedOrderAmountByUserIdAndCreatedAtBetween(
            @Param("userId") UUID userId,
            @Param("excludedStatus") OrderStatus excludedStatus,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select o
    from Order o
    where o.id = :orderId
      and o.deletedAt is null
    """)
    Optional<Order> findByIdForUpdate(
            @Param("orderId") UUID orderId
    );
}
