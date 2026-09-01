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
                        "123456", "8610000000", "4180000", "15.20", "1.35", "4605.00", "51850.00"
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
}
