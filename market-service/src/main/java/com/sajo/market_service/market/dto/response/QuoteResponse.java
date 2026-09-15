package com.sajo.market_service.market.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.dto.kis.KisQuoteResponse;
import com.sajo.market_service.market.dto.kis.KisRealtimePriceMessage;
import com.sajo.market_service.market.exception.MarketErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;

/** Market 내부 현재가 모델. fetchedAt은 실제 체결 시각이 아니라 KIS 응답을 받은 시각이다. */
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuoteResponse(
        String stockCode,
        Long currentPrice,
        Long openPrice,
        Long highPrice,
        Long lowPrice,
        Long previousClosePrice,
        Long changePrice,
        BigDecimal changeRate,
        Long accumulatedVolume,
        Long tradeAmount,
        Long marketCapitalization,
        BigDecimal per,
        BigDecimal pbr,
        BigDecimal eps,
        BigDecimal bps,
        String baseTime,
        Instant fetchedAt
) {

    public QuoteResponse(
            String stockCode, Long currentPrice, Long openPrice, Long highPrice, Long lowPrice,
            Long previousClosePrice, Long changePrice, BigDecimal changeRate, Long accumulatedVolume,
            Long tradeAmount, Long marketCapitalization, BigDecimal per, BigDecimal pbr, BigDecimal eps, BigDecimal bps
    ) {
        this(stockCode, currentPrice, openPrice, highPrice, lowPrice, previousClosePrice, changePrice, changeRate,
                accumulatedVolume, tradeAmount, marketCapitalization, per, pbr, eps, bps, null, null);
    }

    public QuoteResponse(
            String stockCode, Long currentPrice, Long openPrice, Long highPrice, Long lowPrice,
            Long previousClosePrice, Long changePrice, BigDecimal changeRate, Long accumulatedVolume,
            Long tradeAmount, Long marketCapitalization, BigDecimal per, BigDecimal pbr, BigDecimal eps,
            BigDecimal bps, String baseTime
    ) {
        this(stockCode, currentPrice, openPrice, highPrice, lowPrice, previousClosePrice, changePrice, changeRate,
                accumulatedVolume, tradeAmount, marketCapitalization, per, pbr, eps, bps, baseTime, null);
    }

    public static QuoteResponse from(KisQuoteResponse response, String stockCode) {
        return from(response, stockCode, null);
    }

    public static QuoteResponse from(KisQuoteResponse response, String stockCode, Instant fetchedAt) {
        if (response == null) {
            throw new BusinessException(
                    MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID,
                    "KIS 현재가 응답이 비어 있습니다."
            );
        }
        KisQuoteResponse.KisQuoteOutput output = response.output();
        if (output == null) {
            throw new BusinessException(
                    MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID,
                    "KIS 현재가 응답에 output이 없습니다."
            );
        }
        return new QuoteResponse(
                stockCode,
                toLong(output.currentPrice()),
                toLong(output.openPrice()),
                toLong(output.highPrice()),
                toLong(output.lowPrice()),
                toLong(output.previousClosePrice()),
                toLong(output.changePrice()),
                toBigDecimal(output.changeRate()),
                toLong(output.accumulatedVolume()),
                toLong(output.tradeAmount()),
                toLong(output.marketCapitalization()),
                toOptionalBigDecimal(output.per(), stockCode, "per"),
                toOptionalBigDecimal(output.pbr(), stockCode, "pbr"),
                toOptionalBigDecimal(output.eps(), stockCode, "eps"),
                toOptionalBigDecimal(output.bps(), stockCode, "bps"),
                null,
                fetchedAt
        );
    }

    /**
     * KIS WebSocket 실시간 체결가 레코드로 현재가 관련 필드만 갱신한 새 QuoteResponse를 만든다.
     * PER/PBR/EPS/BPS/시가총액은 실시간 체결가에 포함되지 않는 필드라 REST로 캐시돼 있던
     * {@code previous} 값을 그대로 보존한다(없으면 null).
     */
    public static QuoteResponse fromRealtime(KisRealtimePriceMessage message, QuoteResponse previous, Instant fetchedAt) {
        if (message == null) {
            throw new BusinessException(
                    MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID,
                    "KIS 실시간 체결가 메시지가 비어 있습니다."
            );
        }
        return new QuoteResponse(
                message.stockCode(),
                toLong(message.currentPrice()),
                toLong(message.openPrice()),
                toLong(message.highPrice()),
                toLong(message.lowPrice()),
                previous != null ? previous.previousClosePrice() : null,
                toSignedChangePrice(message),
                toBigDecimal(message.changeRate()),
                toLong(message.accumulatedVolume()),
                toLong(message.accumulatedTradeAmount()),
                previous != null ? previous.marketCapitalization() : null,
                previous != null ? previous.per() : null,
                previous != null ? previous.pbr() : null,
                previous != null ? previous.eps() : null,
                previous != null ? previous.bps() : null,
                null,
                fetchedAt
        );
    }

    /**
     * KIS는 전일대비값의 부호를 changeSign(1~5)으로 별도 표기한다. changePrice 필드 자체가 이미 부호를
     * 포함해 내려오는 경우도 실제 캡처 샘플에서 확인됐다(예: 하락 시 "-10250"). 그래서 changeSign만
     * 신뢰하지 않고 Math.abs로 부호를 제거한 뒤, 하락(4=하한가, 5=하락)일 때만 다시 음수로 뒤집어
     * REST 응답(prdy_vrss, 부호 포함)과 부호 체계를 맞춘다 — changePrice의 원래 부호 표기 방식과
     * 무관하게 항상 안전하게 동작한다.
     */
    private static Long toSignedChangePrice(KisRealtimePriceMessage message) {
        Long changePrice = toLong(message.changePrice());
        if (changePrice == null) {
            return null;
        }
        String sign = message.changeSign();
        boolean isFall = "4".equals(sign) || "5".equals(sign);
        return isFall ? -Math.abs(changePrice) : changePrice;
    }

    private static Long toLong(String value) {
        return value == null || value.isBlank() ? null : Long.valueOf(value);
    }

    private static BigDecimal toBigDecimal(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private static BigDecimal toOptionalBigDecimal(String value, String stockCode, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            log.warn("KIS 현재가 선택 지표 파싱을 건너뜁니다. stockCode={}, field={}, exceptionType={}",
                    stockCode, fieldName, exception.getClass().getSimpleName());
            return null;
        }
    }

}
