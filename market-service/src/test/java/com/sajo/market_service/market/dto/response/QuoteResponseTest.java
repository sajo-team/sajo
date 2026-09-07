package com.sajo.market_service.market.dto.response;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.dto.kis.KisQuoteResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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
                        "123456", "8610000000", "4180000", "15.20", "1.35", "4605.00", "51850.00", "20260904", "143000"
                )
        );

        QuoteResponse quote = QuoteResponse.from(response, "005930");

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
        assertEquals("2026-09-04T14:30:00+09:00", quote.baseTime());
        assertEquals(java.time.LocalDate.of(2026, 9, 4), quote.businessDate());
    }

    @Test
    void convertsBlankOptionalFieldsToNull() {
        KisQuoteResponse response = new KisQuoteResponse(
                "0",
                "MCA00000",
                "정상처리 되었습니다.",
                new KisQuoteResponse.KisQuoteOutput(
                        "70000", "", null, "", null, "", null,
                        "", null, "", "", null, "", "", "20260904", "143000"
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
    void returnsNullBaseTimeWhenBusinessDateIsMissingOrBlank() {
        assertNull(QuoteResponse.from(responseWithBaseTime(null, "143000"), "005930").baseTime());
        assertNull(QuoteResponse.from(responseWithBaseTime(" ", "143000"), "005930").baseTime());
    }

    @Test
    void returnsNullBaseTimeWhenBusinessDateIsMalformedOrDoesNotExist() {
        assertNull(QuoteResponse.from(responseWithBaseTime("invalid", "143000"), "005930").baseTime());
        assertNull(QuoteResponse.from(responseWithBaseTime("20260230", "143000"), "005930").baseTime());
        assertNull(QuoteResponse.from(responseWithBaseTime("invalid", "143000"), "005930").businessDate());
    }

    @Test
    void returnsNullBaseTimeWhenContractTimeIsMissingOrBlank() {
        assertNull(QuoteResponse.from(responseWithBaseTime("20260904", null), "005930").baseTime());
        assertNull(QuoteResponse.from(responseWithBaseTime("20260904", " "), "005930").baseTime());
    }

    @Test
    void returnsNullBaseTimeWhenContractTimeIsMalformedOrDoesNotExist() {
        assertNull(QuoteResponse.from(responseWithBaseTime("20260904", "invalid"), "005930").baseTime());
        assertNull(QuoteResponse.from(responseWithBaseTime("20260904", "246000"), "005930").baseTime());
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

    private KisQuoteResponse responseWithBaseTime(String businessDate, String contractTime) {
        return new KisQuoteResponse("0", "MCA00000", "정상",
                new KisQuoteResponse.KisQuoteOutput(
                        "70000", "", "", "", "", "", "", "", "", "", "", "", "", "", businessDate, contractTime));
    }

    private KisQuoteResponse responseWithIndicators(String per, String pbr, String eps, String bps) {
        return new KisQuoteResponse("0", "MCA00000", "정상",
                new KisQuoteResponse.KisQuoteOutput(
                        "70000", "", "", "", "", "", "", "", "", "", per, pbr, eps, bps, "20260904", "143000"));
    }
}
