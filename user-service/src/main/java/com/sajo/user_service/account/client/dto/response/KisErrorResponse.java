package com.sajo.user_service.account.client.dto.response;

public record KisErrorResponse(
        String error_code,
        String error_description
) {
}
