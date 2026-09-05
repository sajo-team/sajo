package com.sajo.common.jwt;
 
import java.util.UUID;
 
// role은 도입 이전에 발급된 토큰을 검증할 때 null일 수 있다 - 호출하는 쪽에서
// null을 "권한 없음"으로 다루면 된다
public record JwtClaims(UUID userId, String role) {
}
