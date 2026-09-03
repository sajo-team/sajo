package com.sajo.user_service.account.client.dto.response;

public record KISErrorResponse(
        String error_code,
        String error_description
) {
}
