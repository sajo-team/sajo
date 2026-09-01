package com.sajo.market_service.market.repository.command;

import com.sajo.market_service.market.domain.MarketStockIndicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface MarketStockIndicatorCommandRepository extends JpaRepository<MarketStockIndicator, UUID> {

    boolean existsByStockIdAndReferenceDate(UUID stockId, LocalDate referenceDate);
}
