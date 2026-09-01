package com.sajo.market_service.market.domain;

import com.sajo.common.entity.BaseUpdatableEntity;
import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.exception.MarketErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 종목 기본정보 (m_market_stocks).
 * stock_code는 조회용 비즈니스 키이며, 내부 참조(FK)는 UUID인 id를 사용한다.
 */
@Getter
@Entity
@Table(name = "m_market_stocks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketStock extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "stock_code", nullable = false, unique = true, length = 20)
    private String stockCode;

    @Column(name = "stock_name", nullable = false, length = 100)
    private String stockName;

    @Column(name = "market_type", nullable = false, length = 20)
    private String marketType;

    @Column(name = "industry_code", length = 20)
    private String industryCode;

    @Column(name = "listed_shares")
    private Long listedShares;

    @Column(name = "market_cap", precision = 20, scale = 0)
    private BigDecimal marketCap;

    private MarketStock(
            String stockCode,
            String stockName,
            String marketType,
            String industryCode,
            Long listedShares,
            BigDecimal marketCap
    ) {
        this.stockCode = stockCode;
        this.stockName = stockName;
        this.marketType = marketType;
        this.industryCode = industryCode;
        this.listedShares = listedShares;
        this.marketCap = marketCap;
    }

    public static MarketStock create(
            String stockCode,
            String stockName,
            String marketType,
            String industryCode,
            Long listedShares,
            BigDecimal marketCap
    ) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new BusinessException(
                    MarketErrorCode.INVALID_MARKET_STOCK,
                    "종목코드는 필수입니다."
            );
        }
        if (stockName == null || stockName.isBlank()) {
            throw new BusinessException(
                    MarketErrorCode.INVALID_MARKET_STOCK,
                    "종목명은 필수입니다."
            );
        }
        if (marketType == null || marketType.isBlank()) {
            throw new BusinessException(
                    MarketErrorCode.INVALID_MARKET_STOCK,
                    "시장구분은 필수입니다."
            );
        }
        return new MarketStock(
                stockCode,
                stockName,
                marketType,
                industryCode,
                listedShares,
                marketCap
        );
    }
}
