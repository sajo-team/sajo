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
import java.time.LocalTime;
import java.util.UUID;

/**
 * 종목 현재가·일별 시세 이력 (m_market_stocks_price).
 * source(REST/WEBSOCKET)로 수집 경로를 구분하며, 수정되지 않는 이력 데이터라 BaseEntity(생성 시각만)를 상속한다.
 * REST 일별 시세만 stock_id + date 조합이 유일해야 한다(Partial Unique Index로 강제).
 * 이 Entity는 저장 여부를 스스로 판단하지 않는다 — 저장 시점(스케줄러/동기화 로직 전용) 정책은 Service 계층 책임이다.
 */
@Getter
@Entity
@Table(name = "m_market_stocks_price")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketStockPrice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "stock_id", nullable = false)
    private UUID stockId;

    @Column(nullable = false)
    private LocalDate date;

    private LocalTime time;

    @Column(name = "current_price")
    private Long currentPrice;

    @Column(name = "close_price")
    private Long closePrice;

    @Column(name = "open_price")
    private Long openPrice;

    @Column(name = "high_price")
    private Long highPrice;

    @Column(name = "low_price")
    private Long lowPrice;

    @Column(name = "prev_close_price")
    private Long prevClosePrice;

    @Column(name = "change_price")
    private Long changePrice;

    @Column(name = "change_rate", precision = 10, scale = 4)
    private BigDecimal changeRate;

    // 일봉 API의 acml_vol은 해당 거래일 누적 거래량이므로 accumulatedVolume에만 저장한다.
    private Long volume;

    @Column(name = "accumulated_volume")
    private Long accumulatedVolume;

    @Column(name = "accumulated_trade_amount")
    private Long accumulatedTradeAmount;

    @Column(name = "foreign_ownership_rate", precision = 10, scale = 4)
    private BigDecimal foreignOwnershipRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PriceSource source;

    private MarketStockPrice(
            UUID stockId,
            LocalDate date,
            LocalTime time,
            Long currentPrice,
            Long closePrice,
            Long openPrice,
            Long highPrice,
            Long lowPrice,
            Long prevClosePrice,
            Long changePrice,
            BigDecimal changeRate,
            Long volume,
            Long accumulatedVolume,
            Long accumulatedTradeAmount,
            BigDecimal foreignOwnershipRate,
            PriceSource source
    ) {
        this.stockId = stockId;
        this.date = date;
        this.time = time;
        this.currentPrice = currentPrice;
        this.closePrice = closePrice;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.prevClosePrice = prevClosePrice;
        this.changePrice = changePrice;
        this.changeRate = changeRate;
        this.volume = volume;
        this.accumulatedVolume = accumulatedVolume;
        this.accumulatedTradeAmount = accumulatedTradeAmount;
        this.foreignOwnershipRate = foreignOwnershipRate;
        this.source = source;
    }

    public static MarketStockPrice create(
            UUID stockId,
            LocalDate date,
            LocalTime time,
            Long currentPrice,
            Long closePrice,
            Long openPrice,
            Long highPrice,
            Long lowPrice,
            Long prevClosePrice,
            Long changePrice,
            BigDecimal changeRate,
            Long volume,
            Long accumulatedVolume,
            Long accumulatedTradeAmount,
            BigDecimal foreignOwnershipRate,
            PriceSource source
    ) {
        if (stockId == null) {
            throw new BusinessException(
                    MarketErrorCode.INVALID_MARKET_STOCK_PRICE,
                    "종목 ID는 필수입니다."
            );
        }
        if (date == null) {
            throw new BusinessException(
                    MarketErrorCode.INVALID_MARKET_STOCK_PRICE,
                    "기준일은 필수입니다."
            );
        }
        if (currentPrice == null && closePrice == null) {
            throw new BusinessException(
                    MarketErrorCode.INVALID_MARKET_STOCK_PRICE,
                    "현재가는 필수입니다."
            );
        }
        if (source == null) {
            throw new BusinessException(
                    MarketErrorCode.INVALID_MARKET_STOCK_PRICE,
                    "수집 경로(source)는 필수입니다."
            );
        }
        return new MarketStockPrice(
                stockId,
                date,
                time,
                currentPrice,
                closePrice,
                openPrice,
                highPrice,
                lowPrice,
                prevClosePrice,
                changePrice,
                changeRate,
                volume,
                accumulatedVolume,
                accumulatedTradeAmount,
                foreignOwnershipRate,
                source
        );
    }
}
