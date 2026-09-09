package com.sajo.user_service.account.client.kis.dto.request;

public record AccessTokenRequest(
        String grant_type,
        String appkey,
        String appsecret
) {
}
