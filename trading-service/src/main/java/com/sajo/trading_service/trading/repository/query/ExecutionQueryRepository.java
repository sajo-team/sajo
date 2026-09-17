package com.sajo.trading_service.trading.repository.query;

import com.sajo.trading_service.trading.domain.Execution;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.repository.query.projection.ExecutionAdminQueryProjection;
import com.sajo.trading_service.trading.repository.query.projection.ExecutionQueryProjection;
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
    select new com.sajo.trading_service.trading.repository.query.projection.ExecutionQueryProjection(
        e.id,
        e.orderId,
        o.autoTradingId,
        o.strategyId,
        o.brokerOrderNo,
        e.executedQuantity,
        e.averageExecutionPrice,
        e.totalExecutionAmount,
        e.remainingQuantity,
        e.createdAt,
        e.updatedAt
    )
    from Execution e, Order o
    where e.orderId = o.id
      and o.userId = :userId
      and o.deletedAt is null
      and e.deletedAt is null
      and (:orderId is null or e.orderId = :orderId)
      and (:autoTradingId is null or o.autoTradingId = :autoTradingId)
      and (:strategyId is null or o.strategyId = :strategyId)
    order by e.updatedAt desc
""")
    Page<ExecutionQueryProjection> findByUserId(
            @Param("userId") UUID userId,
            @Param("orderId") UUID orderId,
            @Param("autoTradingId") UUID autoTradingId,
            @Param("strategyId") UUID strategyId,
            Pageable pageable
    );

    @Query("""
        select new com.sajo.trading_service.trading.repository.query.projection.ExecutionQueryProjection(
            e.id,
            e.orderId,
            o.autoTradingId,
            o.strategyId,
            o.brokerOrderNo,
            e.executedQuantity,
            e.averageExecutionPrice,
            e.totalExecutionAmount,
            e.remainingQuantity,
            e.createdAt,
            e.updatedAt
        )
        from Execution e, Order o
        where e.id = :executionId
          and e.orderId = o.id
          and o.userId = :userId
          and o.deletedAt is null
          and e.deletedAt is null
    """)
    Optional<ExecutionQueryProjection> findByIdAndUserId(
            @Param("executionId") UUID executionId,
            @Param("userId") UUID userId
    );

    @Query("""
    select new com.sajo.trading_service.trading.repository.query.projection.ExecutionAdminQueryProjection(
        e.id,
        e.orderId,
        o.userId,
        o.autoTradingId,
        o.strategyId,
        o.stockCode,
        o.orderType,
        o.brokerOrderNo,
        e.executedQuantity,
        e.averageExecutionPrice,
        e.totalExecutionAmount,
        e.remainingQuantity,
        e.createdAt,
        e.updatedAt
    )
    from Execution e, Order o
    where e.orderId = o.id
      and o.deletedAt is null
      and e.deletedAt is null
      and (:userId is null or o.userId = :userId)
      and (:orderId is null or e.orderId = :orderId)
      and (:autoTradingId is null or o.autoTradingId = :autoTradingId)
      and (:strategyId is null or o.strategyId = :strategyId)
      and (:stockCode is null or o.stockCode = :stockCode)
      and (:orderType is null or o.orderType = :orderType)
    order by e.updatedAt desc
""")
    Page<ExecutionAdminQueryProjection> findAllForAdmin(
            @Param("userId") UUID userId,
            @Param("orderId") UUID orderId,
            @Param("autoTradingId") UUID autoTradingId,
            @Param("strategyId") UUID strategyId,
            @Param("stockCode") String stockCode,
            @Param("orderType") OrderType orderType,
            Pageable pageable
    );
}