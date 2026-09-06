package com.sajo.user_service.auth.service.query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

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

    // INCR와 EXPIRE를 하나의 Lua 스크립트로 묶어 원자적으로 실행한다 - 리뷰 반영:
    // 두 호출을 따로따로 하면(increment 성공 직후 expire만 실패하는 경우) 카운터가
    // TTL 없이 남을 수 있고, 그러면 이후 실패가 쌓여 5회에 도달했을 때 자동 만료 없이
    // 영구 잠금이 된다(로그인에 성공하기 전까지 안 풀림). Redis의 EVAL은 스크립트
    // 전체를 하나의 원자적 연산으로 실행하므로 이 틈이 생기지 않는다.
    private static final RedisScript<Long> INCREMENT_AND_EXPIRE_ON_FIRST_SCRIPT = RedisScript.of(
            "local count = redis.call('INCR', KEYS[1]) "
                    + "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return count",
            Long.class
    );

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
            stringRedisTemplate.execute(
                    INCREMENT_AND_EXPIRE_ON_FIRST_SCRIPT,
                    List.of(key),
                    String.valueOf(LOCK_DURATION.getSeconds())
            );
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
