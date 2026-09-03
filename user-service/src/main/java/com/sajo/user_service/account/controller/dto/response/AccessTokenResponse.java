package com.sajo.user_service.account.controller.dto.response;

public record AccessTokenResponse(
        String accessToken,
        String appKey,
        String secretKey
) {
}
