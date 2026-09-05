package com.sajo.trading_service.trading.client.dto.response;

public record AccountTokenResponse(
        String accessToken,
        String appKey,
        String secretKey
) {
}
