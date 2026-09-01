package com.sajo.market_service.market.repository.command;

import com.sajo.market_service.market.domain.MarketStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MarketStockCommandRepository extends JpaRepository<MarketStock, UUID> {

    Optional<MarketStock> findByStockCode(String stockCode);

    boolean existsByStockCode(String stockCode);
}
