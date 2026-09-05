package com.sajo.user_service.auth.service.query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

// 이메일 기준 로그인 실패 횟수를 Redis에 기록해 무차별 대입 공격을 막는다.
// 성공하면 카운터를 지우고, 실패가 임계치를 넘으면 일정 시간 로그인 자체를 막는다.
//
// StringRedisTemplate을 쓰는 이유: CommonRedisAutoConfiguration이 제공하는 기본
// redisTemplate은 값(value)을 JSON으로 직렬화(GenericJacksonJsonRedisSerializer)하는데,
// 이렇게 저장된 값은 Redis의 원자적 INCR 연산과 호환되지 않는다(순수 정수 문자열이
// 아니게 됨). 카운터 용도로는 값도 문자열로 다루는 StringRedisTemplate이 맞다.
//
// fail-open 정책 - 리뷰 반영: Redis 장애 시 예외를 그대로 던지면 로그인 로직 전체가
// 막혀서(정상 사용자 포함) 이 기능 도입 이전엔 없던 단일 장애점이 생긴다. 브루트포스
// 방지 기능이 일시적으로 꺼지는 것보다 로그인 자체가 완전히 막히는 게 더 큰 장애라고
// 판단해, Redis 장애 시에는 각 메서드가 "안전한 기본값"으로 넘어가도록 한다
// (잠금 확인은 통과시키고, 기록은 조용히 실패로 남긴다).
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String KEY_PREFIX = "user-service:login-attempts:";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final StringRedisTemplate stringRedisTemplate;

    public boolean isLocked(String email) {
        try {
            String count = stringRedisTemplate.opsForValue().get(KEY_PREFIX + email);
            return count != null && Integer.parseInt(count) >= MAX_ATTEMPTS;
        } catch (RuntimeException e) {
            log.warn("Redis 조회 실패로 로그인 잠금 여부를 확인하지 못해 통과시킴(fail-open)", e);
            return false;
        }
    }

    public void recordFailure(String email) {
        try {
            String key = KEY_PREFIX + email;
            Long newCount = stringRedisTemplate.opsForValue().increment(key);
            // 새로 만들어진 키(첫 실패)일 때만 TTL을 설정한다 - 매번 설정하면 계속
            // 실패할 때마다 만료 시각이 뒤로 밀려서 사실상 영구 잠금이 될 수 있다
            if (newCount != null && newCount == 1L) {
                stringRedisTemplate.expire(key, LOCK_DURATION);
            }
        } catch (RuntimeException e) {
            log.warn("Redis 기록 실패로 로그인 실패 횟수를 남기지 못함", e);
        }
    }

    public void recordSuccess(String email) {
        try {
            stringRedisTemplate.delete(KEY_PREFIX + email);
        } catch (RuntimeException e) {
            log.warn("Redis 기록 실패로 로그인 성공 시 카운터 초기화를 하지 못함", e);
        }
    }
}
