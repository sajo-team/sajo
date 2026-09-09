package com.sajo.user_service.account.client.kis.dto.request;

public record AccessTokenRevokeRequest (
        String appkey,
        String appsecret,
        String token
){
}
