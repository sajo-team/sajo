package com.sajo.market_service.market.kafka.dto;

import com.sajo.market_service.market.domain.PriceSource;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@link MarketPriceUpdatedEvent}의 payload (Kafka Event Naming Convention 확정 스펙).
 *
 * <p>stockName/market(코스피·코스닥 등)은 이번 범위(#236)에서는 payload에 포함하지 않는다 — 이
 * 데이터는 {@code MarketStock} 테이블에만 있어 실시간 tick마다 채우려면 별도 캐싱 설계가 필요한데,
 * Trading과 협의한 결과 이번 범위에서는 제외하기로 했다. 필요해지면 별도 이슈로 추가한다.</p>
 *
 * <p>{@code volume}은 KIS 실시간 체결가의 누적거래량({@code acml_vol})이다 — 이 tick 하나의 체결량이
 * 아니라, 당일 누적된 거래량이라는 점에 유의한다.</p>
 */
public record MarketPricePayload(
        String stockCode,
        Long currentPrice,
        Long changePrice,
        BigDecimal changeRate,
        Long volume,
        Instant tradedAt,
        PriceSource source
) {
}
