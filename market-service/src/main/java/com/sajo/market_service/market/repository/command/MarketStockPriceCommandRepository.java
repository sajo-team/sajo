package com.sajo.market_service.market.repository.command;

import com.sajo.market_service.market.domain.MarketStockPrice;
import com.sajo.market_service.market.domain.PriceSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * 기존 날짜 일괄 조회 쿼리 담당
 */
@Repository
public interface MarketStockPriceCommandRepository extends JpaRepository<MarketStockPrice, UUID> {

    @Query("""
            select price.date
            from MarketStockPrice price
            where price.stockId = :stockId
              and price.date between :startDate and :endDate
              and price.time is null
              and price.source = :source
            """)
    Set<LocalDate> findDatesByStockIdAndDateBetweenAndTimeIsNullAndSource(
            @Param("stockId") UUID stockId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("source") PriceSource source
    );
}
