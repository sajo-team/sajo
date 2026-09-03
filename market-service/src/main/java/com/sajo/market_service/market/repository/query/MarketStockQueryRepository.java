package com.sajo.market_service.market.repository.query;

import com.sajo.market_service.market.domain.MarketStock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MarketStockQueryRepository extends JpaRepository<MarketStock, UUID> {

    Page<MarketStock> findByMarketType(String marketType, Pageable pageable);

    @Query("""
            select stock
            from MarketStock stock
            where lower(stock.stockName) like lower(concat('%', :keyword, '%')) escape '!'
               or stock.stockCode like concat('%', :keyword, '%') escape '!'
            """)
    Page<MarketStock> searchByStockNameOrStockCode(@Param("keyword") String keyword, Pageable pageable);

    Optional<MarketStock> findByStockCode(String stockCode);
}
