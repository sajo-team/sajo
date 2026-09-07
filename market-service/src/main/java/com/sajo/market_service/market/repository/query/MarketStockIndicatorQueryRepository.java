package com.sajo.market_service.market.repository.query;

import com.sajo.market_service.market.domain.MarketStockIndicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MarketStockIndicatorQueryRepository extends JpaRepository<MarketStockIndicator, UUID> {

    /**
     * 기준일과 생성일 기준으로 해당 종목의 최신 투자지표를 조회한다.
     *
     * @param stockId
     * @return
     */
    Optional<MarketStockIndicator> findTopByStockIdOrderByReferenceDateDescCreatedAtDesc(UUID stockId);
}
