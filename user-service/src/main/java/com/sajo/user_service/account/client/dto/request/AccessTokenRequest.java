package com.sajo.user_service.account.client.dto.request;

public record AccessTokenRequest(
        String grant_type,
        String appkey,
        String appsecret
) {
}
