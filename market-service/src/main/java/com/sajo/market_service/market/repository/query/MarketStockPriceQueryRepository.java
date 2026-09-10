package com.sajo.market_service.market.repository.query;

import com.sajo.market_service.market.domain.MarketStockPrice;
import com.sajo.market_service.market.domain.PriceSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface MarketStockPriceQueryRepository extends JpaRepository<MarketStockPrice, UUID> {

    //일별 시세 조회
    @Query("""
            select price
            from MarketStockPrice price
            where price.stockId = :stockId
              and price.time is null
              and price.source = :source
              and price.closePrice is not null
            order by price.date desc
            """)
    List<MarketStockPrice> findRecentDailyRestPrices(
            @Param("stockId") UUID stockId,
            @Param("source") PriceSource source,
            Pageable pageable
    );

    @Query("""
            select price
            from MarketStockPrice price
            where price.stockId = :stockId
              and price.time is null
              and price.source = :source
              and price.closePrice is not null
              and price.date between :startDate and :endDate
            order by price.date asc
            """)
    List<MarketStockPrice> findDailyRestPrices(
            @Param("stockId") UUID stockId,
            @Param("source") PriceSource source,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
