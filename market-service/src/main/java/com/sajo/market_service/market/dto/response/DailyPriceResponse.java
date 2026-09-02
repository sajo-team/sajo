package com.sajo.market_service.market.dto.response;

import com.sajo.market_service.market.dto.kis.KisDailyPriceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public record DailyPriceResponse(LocalDate tradeDate, Long openPrice, Long highPrice, Long lowPrice,
                                 Long closePrice, Long volume, Long tradeAmount) {
    private static final Logger log = LoggerFactory.getLogger(DailyPriceResponse.class);

    //잘못된 KIS 데이터 파싱 처리 추가
    public static Optional<DailyPriceResponse> from(KisDailyPriceResponse.KisDailyPriceOutput output, String stockCode) {
        LocalDate tradeDate = parseRequiredDate(output.tradeDate(), stockCode);
        Long closePrice = parseRequiredLong(output.closePrice(), stockCode, output.tradeDate(), "closePrice");
        if (tradeDate == null || closePrice == null) {
            return Optional.empty();
        }

        return Optional.of(new DailyPriceResponse(
                tradeDate,
                parseOptionalLong(output.openPrice(), stockCode, output.tradeDate(), "openPrice"),
                parseOptionalLong(output.highPrice(), stockCode, output.tradeDate(), "highPrice"),
                parseOptionalLong(output.lowPrice(), stockCode, output.tradeDate(), "lowPrice"),
                closePrice,
                parseOptionalLong(output.volume(), stockCode, output.tradeDate(), "volume"),
                parseOptionalLong(output.tradeAmount(), stockCode, output.tradeDate(), "tradeAmount")
        ));
    }

    private static LocalDate parseRequiredDate(String value, String stockCode) {
        try {
            return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (RuntimeException exception) {
            log.warn("KIS 일별 시세 행을 건너뜁니다. stockCode={}, tradeDate={}, field=tradeDate, exceptionType={}",
                    stockCode, value, exception.getClass().getSimpleName());
            return null;
        }
    }

    private static Long parseRequiredLong(String value, String stockCode, String tradeDate, String fieldName) {
        try {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("required value is blank");
            }
            return Long.valueOf(value);
        } catch (RuntimeException exception) {
            log.warn("KIS 일별 시세 행을 건너뜁니다. stockCode={}, tradeDate={}, field={}, exceptionType={}",
                    stockCode, tradeDate, fieldName, exception.getClass().getSimpleName());
            return null;
        }
    }

    private static Long parseOptionalLong(String value, String stockCode, String tradeDate, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseLong(value, stockCode, tradeDate, fieldName);
    }

    private static Long parseLong(String value, String stockCode, String tradeDate, String fieldName) {
        try {
            if (value == null || value.isBlank()) {
                return null;
            }
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            log.warn("KIS 일별 시세 숫자 필드를 null로 처리합니다. stockCode={}, tradeDate={}, field={}, exceptionType={}",
                    stockCode, tradeDate, fieldName, exception.getClass().getSimpleName());
            return null;
        }
    }
}
