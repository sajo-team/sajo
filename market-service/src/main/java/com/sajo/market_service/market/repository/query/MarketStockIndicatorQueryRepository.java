package com.sajo.market_service.market.repository.query;

import com.sajo.market_service.market.domain.MarketStockIndicator;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    Optional<MarketStockIndicator> findTopByStockIdAndFinancialPeriodTypeIsNotNullAndFinancialReferenceYearMonthIsNotNullOrderByFinancialReferenceYearMonthDescFinancialFetchedAtDesc(UUID stockId);

    /**
     * 종목의 신규 분기 투자지표 이력을 결산연월 내림차순으로 조회한다.
     * 레거시 referenceDate 행은 포함하지 않는다.
     */
    List<MarketStockIndicator> findByStockIdAndFinancialPeriodTypeIsNotNullAndFinancialReferenceYearMonthIsNotNullOrderByFinancialReferenceYearMonthDescFinancialFetchedAtDesc(
            UUID stockId, Pageable pageable);

    /**
     * 신규 분기 스냅샷이 없는 종목을 위한 레거시 투자지표 이력을 기준일 내림차순으로 조회한다.
     */
    List<MarketStockIndicator> findByStockIdAndReferenceDateIsNotNullOrderByReferenceDateDescCreatedAtDesc(
            UUID stockId, Pageable pageable);
}
