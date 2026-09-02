package com.sajo.market_service.market.dto.response;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.dto.kis.KisDailyPriceResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import java.time.LocalDate;

public record DailyPriceResponse(LocalDate tradeDate, Long openPrice, Long highPrice, Long lowPrice,
                                 Long closePrice, Long volume, Long tradeAmount) {
    public static DailyPriceResponse from(KisDailyPriceResponse.KisDailyPriceOutput output) {
        try {
            return new DailyPriceResponse(LocalDate.parse(output.tradeDate(), java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                    Long.valueOf(output.openPrice()), Long.valueOf(output.highPrice()), Long.valueOf(output.lowPrice()),
                    Long.valueOf(output.closePrice()), Long.valueOf(output.volume()), Long.valueOf(output.tradeAmount()));
        } catch (RuntimeException exception) {
            throw new BusinessException(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID, "KIS 일별 시세 응답 형식이 올바르지 않습니다.");
        }
    }
}
