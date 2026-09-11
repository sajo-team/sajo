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
