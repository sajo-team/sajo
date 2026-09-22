package com.sajo.user_service.account.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@RequiredArgsConstructor
@Component
public class KisTokenCacheLock {

    // KIS OAuth 호출 최대 시간(connect 5s + read 30s = 35s, KisRestClientConfiguration)보다 여유 있게 -
    // 락 홀더가 죽었을 때 자동 해제되는 상한선
    public static final Duration LOCK_TTL = Duration.ofSeconds(40);
    // 락을 못 잡은 요청이 대기하다 포기하기까지의 최대 시간 - LOCK_TTL보다 반드시 여유 있게 커야 한다.
    // 같거나 작으면, 홀더가 락 TTL 끝자락에 캐시를 막 채운 순간 대기자가 먼저 타임아웃해버리는
    // 경합이 생긴다 (다른 서비스에서 실제로 발생했던 버그 패턴).
    public static final Duration WAIT_TIMEOUT = Duration.ofSeconds(42);
    // 락 재시도 간격
    public static final Duration RETRY_INTERVAL = Duration.ofMillis(50);

    // access token / approval key 둘 다 이 락을 공유해서 쓴다 (key로 구분)
    private static final String LOCK_KEY_PREFIX = "user-service:kis:lock:";

    // Lua compare-and-delete: Redis의 Lock 값 == 내가 가진 Token일 때만 삭제, 아니면 아무것도 안 함
    // (내가 건 락만 지우기 위함 - 락 TTL 만료 후 남이 새로 잡은 락을 잘못 지우는 것 방지)
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;

    public boolean tryLock(String key, String lockToken, Duration lockTtl) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                .setIfAbsent(createLockKey(key), lockToken, lockTtl)
        );
    }

    public void unlock(String key, String lockToken) {
        stringRedisTemplate.execute(UNLOCK_SCRIPT, List.of(createLockKey(key)), lockToken);
    }

    private String createLockKey(String key) {
        return LOCK_KEY_PREFIX + key;
    }
}
