package com.sajo.trading_service.ai_risk.exception;

import com.sajo.common.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AiRiskErrorCode implements ErrorCode {
    USER_ID_REQUIRED( HttpStatus.BAD_REQUEST, "AI_RISK_0001", "사용자 ID는 필수입니다."),
    STRATEGY_ID_REQUIRED(HttpStatus.BAD_REQUEST, "AI_RISK_0002", "전략 ID는 필수입니다."),
    BACKTEST_ID_REQUIRED(HttpStatus.BAD_REQUEST, "AI_RISK_0003", "백테스트 ID는 필수입니다."),
    STRATEGY_ACCESS_DENIED(HttpStatus.FORBIDDEN, "AI_RISK_0004", "해당 전략에 접근할 권한이 없습니다."),
    BACKTEST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "AI_RISK_0005", "해당 백테스트에 접근할 권한이 없습니다."),
    STRATEGY_BACKTEST_MISMATCH(HttpStatus.BAD_REQUEST, "AI_RISK_0006", "백테스트가 해당 전략의 결과가 아닙니다."),
    BACKTEST_NOT_COMPLETED(HttpStatus.CONFLICT, "AI_RISK_0007", "완료되지 않은 백테스트는 AI 위험 분석을 요청할 수 없습니다."),
    ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "AI_RISK_0008","AI 위험 분석을 찾을 수 없습니다."),
    AI_PROMPT_VERSION_CONFLICT(HttpStatus.CONFLICT, "AI_RISK_0009", "프롬프트 등록 중 충돌이 발생했습니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
