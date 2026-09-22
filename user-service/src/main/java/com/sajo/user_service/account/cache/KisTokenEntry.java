package com.sajo.user_service.account.cache;

import java.time.Duration;

// KIS 토큰 문자열 + TTL을 함께 담는 값 타입.
// 로컬 캐시 저장 값이 이 타입으로 표현된다.
public record KisTokenEntry(String value, Duration ttl) {
}
