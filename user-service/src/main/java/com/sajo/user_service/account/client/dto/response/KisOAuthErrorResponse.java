package com.sajo.user_service.account.client.dto.response;

// kis oauth(토큰 발급/승인키/폐기) 에러 응답
public record KisOAuthErrorResponse(
        String error_code,
        String error_description
) implements KisErrorInfo {

    @Override
    public String code() {
        return error_code;
    }

    @Override
    public String message() {
        return error_description;
    }
}
