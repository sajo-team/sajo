package com.sajo.user_service.account.controller.dto.response;

import com.sajo.user_service.account.domain.EventType;
import com.sajo.user_service.account.domain.KisTokenLog;
import com.sajo.user_service.account.domain.KisTokenType;

import java.time.Instant;
import java.util.UUID;

// 관리자용 목록 조회 - 사용자+토큰타입 조합별 최신 토큰 발급 상태 1건
public record TokenStatusResponse(
        UUID userId,
        KisTokenType tokenType,
        EventType eventType,
        String errorCode,
        String errorMessage,
        Instant createdAt
) {
    public static TokenStatusResponse from(KisTokenLog log) {
        return new TokenStatusResponse(
                log.getUserId(), log.getTokenType(), log.getEventType(), log.getErrorCode(), log.getErrorMessage(),
                log.getCreatedAt());
    }
}
