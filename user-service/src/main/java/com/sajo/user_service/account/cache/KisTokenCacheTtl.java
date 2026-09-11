package com.sajo.user_service.account.cache;

import java.time.Duration;

// KisTokenCacheQueryService/KisTokenCacheCommandService가 같은 기준으로 TTL을 계산하도록 강제하는 용도.
public final class KisTokenCacheTtl {

    // 캐시 만료를 KIS 실제 만료보다 앞당겨서, 만료 직전 토큰을 유효하다고 잘못 캐싱하는 것 방지
    private static final long SAFETY_MARGIN_SECONDS = 60;

    private KisTokenCacheTtl() {
    }

    // 안전마진을 뺀 뒤에도 실제 남은 유효기간을 넘지 않도록 0 밑으로만 clamp한다
    // (고정된 최소 TTL을 강제하면 안전마진 취지에 반해 만료된 토큰을 유효하다고 캐싱할 수 있음)
    public static Duration calculate(long expiresIn) {
        long ttlSeconds = Math.max(expiresIn - SAFETY_MARGIN_SECONDS, 0);
        return Duration.ofSeconds(ttlSeconds);
    }
}
