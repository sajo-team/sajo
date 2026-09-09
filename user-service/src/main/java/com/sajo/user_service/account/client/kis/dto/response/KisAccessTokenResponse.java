package com.sajo.user_service.account.client.kis.dto.response;

public record KisAccessTokenResponse(
        String access_token,
        String token_type,
        float expires_in,
        String access_token_token_expired

){

}
