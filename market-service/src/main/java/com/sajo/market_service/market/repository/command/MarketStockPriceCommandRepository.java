package com.sajo.market_service.market.repository.command;

import com.sajo.market_service.market.domain.MarketStockPrice;
import com.sajo.market_service.market.domain.PriceSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Repository
public interface MarketStockPriceCommandRepository extends JpaRepository<MarketStockPrice, UUID> {

    boolean existsByStockIdAndDateAndTimeAndSource(
            UUID stockId,
            LocalDate date,
            LocalTime time,
            PriceSource source
    );
}
