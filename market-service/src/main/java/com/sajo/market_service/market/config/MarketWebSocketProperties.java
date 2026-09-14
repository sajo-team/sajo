package com.sajo.market_service.market.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@ConfigurationProperties(prefix = "market.websocket")
public record MarketWebSocketProperties(
        boolean enabled,
        String url,
        String systemUserId,
        Duration initialBackoff,
        Duration maxBackoff,
        double backoffMultiplier,
        List<String> targetStockCodes
) {

    private static final String DEFAULT_URL = "ws://ops.koreainvestment.com:31000";
    private static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofSeconds(1);
    private static final Duration DEFAULT_MAX_BACKOFF = Duration.ofSeconds(30);
    private static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;
    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("\\d{6}");

    public MarketWebSocketProperties {
        url = (url == null || url.isBlank()) ? DEFAULT_URL : url;
        initialBackoff = (initialBackoff == null || initialBackoff.isZero() || initialBackoff.isNegative())
                ? DEFAULT_INITIAL_BACKOFF
                : initialBackoff;
        // maxBackoff가 없으면 기본값(30s)을 쓰되, initialBackoff보다 작아지지 않도록 initialBackoff로 올려 잡는다.
        // (예: initialBackoff를 30s보다 크게 설정하고 maxBackoff를 지정하지 않은 경우, 기본값 30s로
        // 되돌리면 운영자 의도와 다르게 초기값보다 짧은 상한이 조용히 적용될 수 있다.)
        Duration resolvedMaxBackoff = (maxBackoff == null) ? DEFAULT_MAX_BACKOFF : maxBackoff;
        maxBackoff = resolvedMaxBackoff.compareTo(initialBackoff) < 0 ? initialBackoff : resolvedMaxBackoff;
        // backoffMultiplier가 1.0 이하이면 재시도 지연이 늘어나지 않거나 오히려 줄어들어 지수 백오프의
        // 의도를 벗어난다. 고정 지연 등 다른 재시도 정책이 필요한 경우 이 프로퍼티로는 표현할 수 없으므로,
        // (문서화된 의도적 동작으로서) 잘못된 설정으로 간주하고 기본값(2.0)으로 대체한다.
        backoffMultiplier = backoffMultiplier > 1.0 ? backoffMultiplier : DEFAULT_BACKOFF_MULTIPLIER;
        // market.websocket.target-stock-codes는 sajo.scheduler.target-stock-codes와 별개의 설정이다.
        // 스케줄러 쪽은 "비어 있으면 전체 종목 대상"으로 해석하지만, WebSocket 실시간 구독은 아직 전체 종목을
        // 구독하는 기능을 지원하지 않으므로 두 설정을 공유하면 "빈 값"의 의미가 서로 어긋난다. 여기서는 비어
        // 있으면 그대로 "구독 대상 없음"으로 취급하며, 호출부(KisWebSocketClient)에서 경고 로그를 남긴다.
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
