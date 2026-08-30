package com.sajo.common.response;


public enum ErrorCode {

    // TODO: 실제 에러 코드로 교체
    INTERNAL_SERVER_ERROR("COMMON-500", "서버 내부 오류가 발생했습니다."),
    INVALID_INPUT("COMMON-400", "잘못된 요청입니다.");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
