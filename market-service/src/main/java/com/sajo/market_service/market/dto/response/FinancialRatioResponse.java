package com.sajo.market_service.market.dto.response;

import com.sajo.market_service.market.dto.kis.KisFinancialRatioResponse;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
public record FinancialRatioResponse(
        YearMonth financialReferenceYearMonth,
        BigDecimal roe,
        Instant fetchedAt
) {
    private static final DateTimeFormatter KIS_YEAR_MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    public static Optional<FinancialRatioResponse> latest(
            List<KisFinancialRatioResponse.KisFinancialRatioOutput> outputs,
            String stockCode,
            Instant fetchedAt
    ) {
        if (outputs == null || fetchedAt == null) {
            return Optional.empty();
        }
        return outputs.stream()
                .map(output -> from(output, stockCode, fetchedAt))
                .flatMap(Optional::stream)
                .max(Comparator.comparing(FinancialRatioResponse::financialReferenceYearMonth));
    }

    private static Optional<FinancialRatioResponse> from(
            KisFinancialRatioResponse.KisFinancialRatioOutput output,
            String stockCode,
            Instant fetchedAt
    ) {
        if (output == null || output.financialReferenceYearMonth() == null
                || output.financialReferenceYearMonth().isBlank()) {
            return Optional.empty();
        }
        try {
            YearMonth period = YearMonth.parse(output.financialReferenceYearMonth().trim(), KIS_YEAR_MONTH);
            BigDecimal roe = parseOptionalDecimal(output.roe(), stockCode, period);
            return Optional.of(new FinancialRatioResponse(period, roe, fetchedAt));
        } catch (DateTimeParseException exception) {
            log.warn("KIS 재무비율 행을 건너뜁니다. stockCode={}, field={}, exceptionType={}",
                    stockCode, "stac_yymm", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static BigDecimal parseOptionalDecimal(String value, String stockCode, YearMonth period) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            log.warn("KIS 재무비율 필드를 null로 처리합니다. stockCode={}, financialPeriod={}, field={}, exceptionType={}",
                    stockCode, period, "roe_val", exception.getClass().getSimpleName());
            return null;
        }
    }
}
