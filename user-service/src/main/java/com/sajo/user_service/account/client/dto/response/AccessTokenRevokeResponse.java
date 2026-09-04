package com.sajo.user_service.account.client.dto.response;

public record AccessTokenRevokeResponse (
        String code,
        String message
){
}
