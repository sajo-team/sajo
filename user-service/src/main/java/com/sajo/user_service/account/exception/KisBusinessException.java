package com.sajo.user_service.account.exception;

import com.sajo.common.code.ErrorCode;
import com.sajo.common.exception.BusinessException;
import lombok.Getter;

// KIS 원본 에러코드/메시지를 내부 진단용으로 보존한다 - message는 그대로 errorCode.getMessage()(사용자 노출용 안전한 문구)를 쓰고,
// kisErrorCode/kisMessage는 GlobalExceptionHandler가 읽지 않아 응답에 노출되지 않는다
@Getter
public class KisBusinessException extends BusinessException {

    private final String kisErrorCode;
    private final String kisMessage;

    public KisBusinessException(ErrorCode errorCode, String kisErrorCode, String kisMessage) {
        super(errorCode);
        this.kisErrorCode = kisErrorCode;
        this.kisMessage = kisMessage;
    }
}
