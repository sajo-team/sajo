package com.sajo.user_service.account.client.dto.request;

public record AccessTokenRevokeRequest (
        String appkey,
        String appsecret,
        String token
){
}
