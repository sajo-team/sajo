package com.sajo.user_service.account.controller.dto.response;

import com.sajo.user_service.account.domain.EventType;
import com.sajo.user_service.account.domain.KisTokenLog;
import com.sajo.user_service.account.domain.KisTokenType;

import java.time.Instant;

// 관리자용 상세 이력 조회 - 사용자 하나의 토큰 발급 이벤트 1건
public record TokenEventResponse(
        KisTokenType tokenType,
        EventType eventType,
        String errorCode,
        String errorMessage,
        Instant createdAt
) {
    public static TokenEventResponse from(KisTokenLog log) {
        return new TokenEventResponse(
                log.getTokenType(), log.getEventType(), log.getErrorCode(), log.getErrorMessage(),
                log.getCreatedAt());
    }
}
