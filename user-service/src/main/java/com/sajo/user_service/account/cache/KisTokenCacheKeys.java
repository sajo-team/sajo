package com.sajo.user_service.account.cache;

import java.util.UUID;

// KisTokenCacheQueryService/KisTokenCacheCommandService가 같은 Redis 키를 보도록 강제하는 용도.

public final class KisTokenCacheKeys {

    private static final String ACCESS_TOKEN_PREFIX = "user-service:kis-access-token:";
    private static final String APPROVAL_KEY_PREFIX = "user-service:kis-approval-key:";

    private KisTokenCacheKeys() {
    }

    public static String accessToken(UUID userId) {
        return ACCESS_TOKEN_PREFIX + userId;
    }

    public static String approvalKey(UUID userId) {
        return APPROVAL_KEY_PREFIX + userId;
    }
}
