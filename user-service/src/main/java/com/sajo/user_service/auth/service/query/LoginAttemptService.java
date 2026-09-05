package com.sajo.user_service.auth.service.query;
 
import lombok.RequiredArgsConstructor;
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
@Component
@RequiredArgsConstructor
public class LoginAttemptService {
 
    private static final String KEY_PREFIX = "user-service:login-attempts:";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
 
    private final StringRedisTemplate stringRedisTemplate;
 
    public boolean isLocked(String email) {
        String count = stringRedisTemplate.opsForValue().get(KEY_PREFIX + email);
        return count != null && Integer.parseInt(count) >= MAX_ATTEMPTS;
    }
 
    public void recordFailure(String email) {
        String key = KEY_PREFIX + email;
        Long newCount = stringRedisTemplate.opsForValue().increment(key);
        // 새로 만들어진 키(첫 실패)일 때만 TTL을 설정한다 - 매번 설정하면 계속
        // 실패할 때마다 만료 시각이 뒤로 밀려서 사실상 영구 잠금이 될 수 있다
        if (newCount != null && newCount == 1L) {
            stringRedisTemplate.expire(key, LOCK_DURATION);
        }
    }
 
    public void recordSuccess(String email) {
        stringRedisTemplate.delete(KEY_PREFIX + email);
    }
}
