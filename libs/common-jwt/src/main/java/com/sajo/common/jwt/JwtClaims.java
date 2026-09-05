package com.sajo.common.jwt;

import java.util.UUID;

// role/sessionId는 도입 이전에 발급된 토큰을 검증할 때 null일 수 있다 - 호출하는 쪽에서
// role의 null은 "권한 없음"으로, sessionId의 null은 "세션 정보 없음(로그아웃 시
// 특정 세션을 지목할 수 없음)"으로 다루면 된다
public record JwtClaims(UUID userId, String role, String sessionId) {
}
