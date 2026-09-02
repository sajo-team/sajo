package com.sajo.market_service.market.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MarketQuoteCacheLock {

    private static final String LOCK_KEY_PREFIX = "market:quote:lock:";
    /**
     * Lua compare-and-delete
     *
     * if Redis의 Lock 값 == 내가 가지고 있는 Token
     *     → Lock 삭제
     * else
     *     → 아무것도 하지 않음
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * SET NX + TTL
     * 이 Key가 없을 때만 Token을 저장하고, 30초 후 자동 삭제해
     *
     * @param stockCode
     * @param lockToken
     * @param lockTtl
     * @return
     */
    public boolean tryLock(String stockCode, String lockToken, Duration lockTtl) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(
                createLockKey(stockCode),
                lockToken,
                lockTtl
        ));
    }

    public void unlock(String stockCode, String lockToken) {
        stringRedisTemplate.execute(UNLOCK_SCRIPT, List.of(createLockKey(stockCode)), lockToken);
    }

    private String createLockKey(String stockCode) {
        return LOCK_KEY_PREFIX + stockCode;
    }
}
