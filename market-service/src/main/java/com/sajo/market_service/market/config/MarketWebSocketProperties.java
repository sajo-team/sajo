package com.sajo.market_service.market.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
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

        // maxBackoff가 initialBackoff보다 작아지는 상황은 (1) maxBackoff를 지정하지 않았는데 initialBackoff를
        // 기본 상한(30s)보다 크게 설정했거나, (2) 운영자가 명시적으로 지정한 max-backoff가 initial-backoff보다
        // 작은 값인 경우 모두 해당한다. 어느 쪽이든 조용히 initial-backoff로 대체하면 운영자가 의도한 값과
        // 실제 적용값이 달라도 알아챌 방법이 없으므로 WARN으로 남긴다.
        Duration resolvedMaxBackoff = (maxBackoff == null) ? DEFAULT_MAX_BACKOFF : maxBackoff;
        if (resolvedMaxBackoff.compareTo(initialBackoff) < 0) {
            log.warn("market.websocket.max-backoff({})가 initial-backoff({})보다 작아 initial-backoff로 대체합니다.",
                    resolvedMaxBackoff, initialBackoff);
            resolvedMaxBackoff = initialBackoff;
        }
        maxBackoff = resolvedMaxBackoff;

        // backoffMultiplier가 1.0 이하이면 재시도 지연이 늘어나지 않거나 오히려 줄어들어 지수 백오프의 의도를
        // 벗어난다. 미설정(0)뿐 아니라 운영자가 의도적으로 1.0 이하 값을 지정한 경우에도 동일하게 적용되므로,
        // 실제 적용값이 설정값과 달라졌음을 알 수 있도록 WARN을 남긴다.
        if (backoffMultiplier <= 1.0) {
            log.warn("market.websocket.backoff-multiplier({})는 1.0보다 커야 하므로 기본값({})으로 대체합니다.",
                    backoffMultiplier, DEFAULT_BACKOFF_MULTIPLIER);
            backoffMultiplier = DEFAULT_BACKOFF_MULTIPLIER;
        }

        // market.websocket.target-stock-codes는 sajo.scheduler.target-stock-codes와 별개의 설정이다.
        // 스케줄러 쪽은 "비어 있으면 전체 종목 대상"으로 해석하지만, WebSocket 실시간 구독은 아직 전체 종목을
        // 구독하는 기능을 지원하지 않으므로 두 설정을 공유하면 "빈 값"의 의미가 서로 어긋난다. 여기서는 비어
        // 있으면 그대로 "구독 대상 없음"으로 취급하며, 호출부(KisWebSocketClient)에서 경고 로그를 남긴다.
        targetStockCodes = normalizeTargetStockCodes(targetStockCodes);

        // system-user-id 누락/형식 오류는 재시도로 해결되지 않는 영구적인 설정 오류다. 검증 없이 두면
        // KisWebSocketClient.connect()의 catch-all에 흡수되어 기동은 정상 완료된 것처럼 보이면서 지수
        // 백오프로 영원히(WARN만 남기며) 재시도를 반복하게 되어 설정 오류를 조기에 발견하기 어렵다.
        // enabled=true인 경우에 한해 기동 시점에 fail-fast 시킨다.
        if (enabled) {
            validateSystemUserId(systemUserId);
        }
    }

    private static void validateSystemUserId(String systemUserId) {
        if (systemUserId == null || systemUserId.isBlank()) {
            throw new IllegalArgumentException(
                    "market.websocket.enabled=true인 경우 market.websocket.system-user-id를 반드시 설정해야 합니다.");
        }
        try {
            UUID.fromString(systemUserId.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "market.websocket.system-user-id는 UUID 형식이어야 합니다. value=" + systemUserId, exception);
        }
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
