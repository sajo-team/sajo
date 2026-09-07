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
import java.util.UUID;

/**
 * 종목 투자지표 이력 (m_market_stocks_indicator).
 * 기준일(reference_date)별 스냅샷이며 수정되지 않는 이력 데이터라 BaseEntity(생성 시각만)를 상속한다.
 * stock_id + reference_date 조합은 유일해야 한다(DB 유니크 제약으로 강제, 애플리케이션에서는 저장 전 존재 여부를 확인한다).
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

    //지표의 기준일
    @Column(name = "reference_date", nullable = false)
    private LocalDate referenceDate;

    //주가가 주당순이익의 몇 배인지
    @Column(precision = 10, scale = 4)
    private BigDecimal per;

    //주가가 주당순자산의 몇 배인지
    @Column(precision = 10, scale = 4)
    private BigDecimal pbr;

    //주식 한 주당 기업이 얼마의 이익을 냈는지
    @Column(precision = 15, scale = 2)
    private BigDecimal eps;

    //기업의 순자산을 주식 한 주당으로 나눈 값
    @Column(precision = 15, scale = 2)
    private BigDecimal bps;

    //기업이 자기자본을 이용해 얼마의 이익을 냈는지
    @Column(precision = 10, scale = 4)
    private BigDecimal roe;

    private MarketStockIndicator(
            UUID stockId,
            LocalDate referenceDate,
            BigDecimal per,
            BigDecimal pbr,
            BigDecimal eps,
            BigDecimal bps,
            BigDecimal roe
    ) {
        this.stockId = stockId;
        this.referenceDate = referenceDate;
        this.per = per;
        this.pbr = pbr;
        this.eps = eps;
        this.bps = bps;
        this.roe = roe;
    }

    public static MarketStockIndicator create(
            UUID stockId,
            LocalDate referenceDate,
            BigDecimal per,
            BigDecimal pbr,
            BigDecimal eps,
            BigDecimal bps,
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
                eps,
                bps,
                roe
        );
    }
}
