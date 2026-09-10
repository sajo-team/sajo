package com.sajo.market_service.market.domain;

import com.sajo.common.entity.BaseEntity;
import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.exception.MarketErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

/**
 * 현재가 기반 평가 지표와 분기 재무 지표를 각각의 기준 시점과 함께 저장하는 Entity
 *
 * 종목 투자지표 이력 (m_market_stocks_indicator).
 * 신규 데이터는 분기 결산연월별 스냅샷이며, PER/PBR 수신 시각과 재무비율 수신 시각을 분리한다.
 *
 * BaseUpdatableEntity에는 이 도메인에 필요하지 않은 수정자·Soft Delete 필드가 포함되어 BaseEntity를 유지한다.
 */
@Getter
@Entity
@Table(
        name = "m_market_stocks_indicator",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_market_stock_indicator_stock_reference_date",
                columnNames = {"stock_id", "reference_date"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketStockIndicator extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "stock_id", nullable = false)
    private UUID stockId;

    // 기존 데이터의 날짜 계약이다. 신규 분기 스냅샷은 결산연월을 별도 컬럼에 보존한다.
    @Column(name = "reference_date")
    private LocalDate referenceDate;

    @Column(name = "valuation_fetched_at")
    private Instant valuationFetchedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "financial_period_type", length = 20)
    private FinancialPeriodType financialPeriodType;

    @Column(name = "financial_reference_year_month", length = 7)
    private String financialReferenceYearMonth;

    @Column(name = "financial_fetched_at")
    private Instant financialFetchedAt;

    // JPA Auditing 대상이 아니며, JDBC upsert SQL이 마지막 정상 upsert 시각으로 직접 관리한다.
    @Column(name = "updated_at")
    private java.time.Instant updatedAt;

    //주가가 주당순이익의 몇 배인지
    @Column(precision = 10, scale = 4)
    private BigDecimal per;

    //주가가 주당순자산의 몇 배인지
    @Column(precision = 10, scale = 4)
    private BigDecimal pbr;

    //기업이 자기자본을 이용해 얼마의 이익을 냈는지
    @Column(precision = 10, scale = 4)
    private BigDecimal roe;

    private MarketStockIndicator(
            UUID stockId,
            LocalDate referenceDate,
            BigDecimal per,
            BigDecimal pbr,
            BigDecimal roe
    ) {
        this.stockId = stockId;
        this.referenceDate = referenceDate;
        this.per = per;
        this.pbr = pbr;
        this.roe = roe;
    }

    public static MarketStockIndicator create(
            UUID stockId,
            LocalDate referenceDate,
            BigDecimal per,
            BigDecimal pbr,
            BigDecimal roe
    ) {
        if (stockId == null) {
            throw new BusinessException(
                    MarketErrorCode.INVALID_MARKET_STOCK_INDICATOR,
                    "종목 ID는 필수입니다."
            );
        }
        if (referenceDate == null) {
            throw new BusinessException(
                    MarketErrorCode.INVALID_MARKET_STOCK_INDICATOR,
                    "기준일은 필수입니다."
            );
        }
        return new MarketStockIndicator(
                stockId,
                referenceDate,
                per,
                pbr,
                roe
        );
    }
}
