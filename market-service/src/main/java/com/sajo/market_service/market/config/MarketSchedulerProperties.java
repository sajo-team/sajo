package com.sajo.market_service.market.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

@ConfigurationProperties(prefix = "sajo.scheduler")
public record MarketSchedulerProperties(
        boolean enabled,
        String systemUserId,
        String dailyPriceCron,
        int pageSize,
        boolean indicatorEnabled,
        String indicatorCron,
        Duration kisRequestInterval,
        List<String> targetStockCodes
) {

    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("\\d{6}");

    public MarketSchedulerProperties {
        pageSize = pageSize > 0 ? pageSize : 100;
        Duration minimumIndicatorInterval = Duration.ofMillis(500);
        kisRequestInterval = kisRequestInterval == null
                || kisRequestInterval.compareTo(minimumIndicatorInterval) < 0
                ? minimumIndicatorInterval
                : kisRequestInterval;
        targetStockCodes = normalizeTargetStockCodes(targetStockCodes);
    }

    private static List<String> normalizeTargetStockCodes(List<String> targetStockCodes) {
        if (targetStockCodes == null) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : targetStockCodes) {
            if (value == null) {
                throw new IllegalArgumentException("target-stock-codes에는 null을 사용할 수 없습니다.");
            }
            for (String code : value.split(",")) {
                String trimmed = code.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (!STOCK_CODE_PATTERN.matcher(trimmed).matches()) {
                    throw new IllegalArgumentException("target-stock-codes는 6자리 숫자여야 합니다.");
                }
                normalized.add(trimmed);
            }
        }
        return List.copyOf(normalized);
    }
}
