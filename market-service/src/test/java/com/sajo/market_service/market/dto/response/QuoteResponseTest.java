package com.sajo.market_service.market.dto.response;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.dto.kis.KisQuoteResponse;
import com.sajo.market_service.market.dto.kis.KisRealtimePriceMessage;
import com.sajo.market_service.market.exception.MarketErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuoteResponseTest {

    @Test
    void mapsKisQuoteFieldsToMarketResponseFields() {
        KisQuoteResponse response = new KisQuoteResponse(
                "0",
                "MCA00000",
                "정상처리 되었습니다.",
                new KisQuoteResponse.KisQuoteOutput(
                        "70000", "69000", "70500", "68800", "69500", "500", "0.7194",
                        "123456", "8610000000", "4180000", "15.20", "1.35", "4605.00", "51850.00"
                )
        );
        Instant fetchedAt = Instant.parse("2026-09-04T08:00:00Z");

        QuoteResponse quote = QuoteResponse.from(response, "005930", fetchedAt);

        assertEquals("005930", quote.stockCode());
        assertEquals(70000L, quote.currentPrice());
        assertEquals(69000L, quote.openPrice());
        assertEquals(70500L, quote.highPrice());
        assertEquals(68800L, quote.lowPrice());
        assertEquals(69500L, quote.previousClosePrice());
        assertEquals(500L, quote.changePrice());
        assertEquals(new BigDecimal("0.7194"), quote.changeRate());
        assertEquals(123456L, quote.accumulatedVolume());
        assertEquals(8610000000L, quote.tradeAmount());
        assertEquals(4180000L, quote.marketCapitalization());
        assertEquals(new BigDecimal("15.20"), quote.per());
        assertEquals(new BigDecimal("1.35"), quote.pbr());
        assertEquals(new BigDecimal("4605.00"), quote.eps());
        assertEquals(new BigDecimal("51850.00"), quote.bps());
        assertEquals(fetchedAt, OffsetDateTime.parse(quote.baseTime()).toInstant());
        assertEquals(fetchedAt, quote.fetchedAt());
    }

    @Test
    void convertsBlankOptionalFieldsToNull() {
        KisQuoteResponse response = new KisQuoteResponse(
                "0",
                "MCA00000",
                "정상처리 되었습니다.",
                new KisQuoteResponse.KisQuoteOutput(
                        "70000", "", null, "", null, "", null,
                        "", null, "", "", null, "", ""
                )
        );

        QuoteResponse quote = QuoteResponse.from(response, "005930");

        assertEquals(70000L, quote.currentPrice());
        assertNull(quote.openPrice());
        assertNull(quote.highPrice());
        assertNull(quote.changeRate());
        assertNull(quote.per());
    }

    @Test
    void convertsEachBlankOptionalIndicatorToNullWithoutDiscardingOtherIndicators() {
        assertNull(QuoteResponse.from(responseWithIndicators(" ", "1.3", "4605", "51850"), "005930").per());
        assertNull(QuoteResponse.from(responseWithIndicators("15.2", " ", "4605", "51850"), "005930").pbr());
        assertNull(QuoteResponse.from(responseWithIndicators("15.2", "1.3", " ", "51850"), "005930").eps());
        assertNull(QuoteResponse.from(responseWithIndicators("15.2", "1.3", "4605", " "), "005930").bps());
    }

    @Test
    void convertsEachMalformedOptionalIndicatorToNullWithoutDiscardingOtherIndicators() {
        assertNull(QuoteResponse.from(responseWithIndicators("invalid", "1.3", "4605", "51850"), "005930").per());
        assertNull(QuoteResponse.from(responseWithIndicators("15.2", "invalid", "4605", "51850"), "005930").pbr());
        assertNull(QuoteResponse.from(responseWithIndicators("15.2", "1.3", "invalid", "51850"), "005930").eps());
        assertNull(QuoteResponse.from(responseWithIndicators("15.2", "1.3", "4605", "invalid"), "005930").bps());
    }

    @Test
    void throwsBusinessExceptionWhenKisResponseOrOutputIsMissing() {
        BusinessException nullResponseException = assertThrows(
                BusinessException.class,
                () -> QuoteResponse.from(null, "005930")
        );
        BusinessException nullOutputException = assertThrows(
                BusinessException.class,
                () -> QuoteResponse.from(new KisQuoteResponse("0", "MCA00000", "정상", null), "005930")
        );

        assertEquals(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID, nullResponseException.getErrorCode());
        assertEquals(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID, nullOutputException.getErrorCode());
    }

    private KisQuoteResponse responseWithIndicators(String per, String pbr, String eps, String bps) {
        return new KisQuoteResponse("0", "MCA00000", "정상",
                new KisQuoteResponse.KisQuoteOutput(
                        "70000", "", "", "", "", "", "", "", "", "", per, pbr, eps, bps));
    }

    @Test
    void fromRealtimeMapsFieldsAndPreservesRestOnlyFieldsFromPrevious() {
        KisRealtimePriceMessage message = KisRealtimePriceMessage.fromFields(new String[]{
                "005930", "150746", "249250", "5", "-10250", "-3.95", "250742.47", "249500", "254500", "248500",
                "249500", "249000", "1", "14871538", "3728926343750", "206240", "176545", "-29695", "81.34",
                "7912656", "6435830", "5", "0.44", "106.69", "090011", "5", "-250", "113225", "5", "-5250",
                "145726", "2", "750", "20260914", "20", "N", "85729", "141952", "343655", "1371668", "0.25",
                "12052370", "123.39", "0", "", "249500", "2"
        });
        QuoteResponse previous = new QuoteResponse(
                "005930", 249000L, 248000L, 250000L, 247000L, 259500L, -500L, new BigDecimal("-0.20"),
                14000000L, 3600000000000L, 4180000L, new BigDecimal("15.20"), new BigDecimal("1.35"),
                new BigDecimal("4605.00"), new BigDecimal("51850.00")
        );
        Instant fetchedAt = Instant.parse("2026-09-14T15:07:46Z");

        QuoteResponse quote = QuoteResponse.fromRealtime(message, previous, fetchedAt);

        assertEquals("005930", quote.stockCode());
        assertEquals(249250L, quote.currentPrice());
        assertEquals(249500L, quote.openPrice());
        assertEquals(254500L, quote.highPrice());
        assertEquals(248500L, quote.lowPrice());
        assertEquals(-10250L, quote.changePrice());
        assertEquals(new BigDecimal("-3.95"), quote.changeRate());
        assertEquals(14871538L, quote.accumulatedVolume());
        assertEquals(3728926343750L, quote.tradeAmount());
        assertEquals(fetchedAt, quote.fetchedAt());
        // 실시간 체결가에는 없는 필드는 REST 캐시(previous) 값을 그대로 보존한다.
        assertEquals(259500L, quote.previousClosePrice());
        assertEquals(4180000L, quote.marketCapitalization());
        assertEquals(new BigDecimal("15.20"), quote.per());
        assertEquals(new BigDecimal("51850.00"), quote.bps());
        // baseTime은 실시간 체결가 메시지에도 별도 기준 시각 필드가 없어 fetchedAt을 그대로 사용한다.
        assertEquals(fetchedAt, OffsetDateTime.parse(quote.baseTime()).toInstant());
    }

    @Test
    void fromRealtimeForcesNegativeChangePriceOnFallSignsEvenIfRawSignIsMissing() {
        KisRealtimePriceMessage message = KisRealtimePriceMessage.fromFields(withChangeSignAndPrice("4", "9000"));

        QuoteResponse quote = QuoteResponse.fromRealtime(message, null, Instant.now());

        assertEquals(-9000L, quote.changePrice());
    }

    @Test
    void fromRealtimeReturnsNullWhenPreviousIsAbsent() {
        KisRealtimePriceMessage message = KisRealtimePriceMessage.fromFields(withChangeSignAndPrice("2", "500"));

        QuoteResponse quote = QuoteResponse.fromRealtime(message, null, Instant.now());

        assertNull(quote.per());
        assertNull(quote.marketCapitalization());
        assertEquals(500L, quote.changePrice());
    }

    @Test
    void fromRealtimeThrowsBusinessExceptionWhenMessageIsNull() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> QuoteResponse.fromRealtime(null, null, Instant.now())
        );

        assertEquals(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID, exception.getErrorCode());
    }

    private String[] withChangeSignAndPrice(String sign, String changePrice) {
        String[] fields = new String[KisRealtimePriceMessage.FIELD_COUNT_PER_RECORD];
        java.util.Arrays.fill(fields, "0");
        fields[0] = "005930";
        fields[1] = "150746";
        fields[2] = "249250";
        fields[3] = sign;
        fields[4] = changePrice;
        fields[5] = "0.5";
        fields[33] = "20260914";
        return fields;
    }
}
