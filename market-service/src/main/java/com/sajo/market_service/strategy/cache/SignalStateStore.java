package com.sajo.market_service.strategy.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * "선점(PROCESSING) → 완료(값) 또는 해제(삭제)"로 이어지는 상태 전이를 Redis Lua로 원자화한
 * 소유권 기반 상태 저장소. {@link com.sajo.market_service.market.cache.MarketQuoteCacheLock}과 같은
 * compare-and-* 패턴을 따르되, 락이 아니라 "이 요청이 이 상태 변경의 주인인가"를 토큰으로 증명한다.
 *
 * <pre>
 * (없음) --claim(token,value)--&gt; PROCESSING:{token} --complete(token,value)--&gt; {value}
 *                                       |
 *                                       +--release(token)--&gt; (없음)
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class SignalStateStore {

    /**
     * 상태가 이미 요청 값(ARGV[2])과 같거나 다른 토큰이 처리 중(PROCESSING:*)이면 0(실패)을,
     * 그 외에는 PROCESSING:{token}으로 원자적으로 선점하고 1(성공)을 반환한다.
     */
    private static final DefaultRedisScript<Long> CLAIM_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('get', KEYS[1]) "
                    + "if current == ARGV[2] then return 0 end "
                    + "if current and string.sub(current, 1, 11) == 'PROCESSING:' then return 0 end "
                    + "redis.call('set', KEYS[1], 'PROCESSING:' .. ARGV[1], 'EX', ARGV[3]) "
                    + "return 1",
            Long.class
    );

    /** 내 토큰(ARGV[1])이 여전히 선점 상태일 때만 완료 값(ARGV[2])으로 전환한다. */
    private static final DefaultRedisScript<Long> COMPLETE_SCRIPT = new DefaultRedisScript<>(
            "local expected = 'PROCESSING:' .. ARGV[1] "
                    + "if redis.call('get', KEYS[1]) == expected then "
                    + "redis.call('set', KEYS[1], ARGV[2], 'EX', ARGV[3]) return 1 "
                    + "else return 0 end",
            Long.class
    );

    /** 내 토큰(ARGV[1])이 여전히 선점 상태일 때만 삭제한다(compare-and-delete). */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "local expected = 'PROCESSING:' .. ARGV[1] "
                    + "if redis.call('get', KEYS[1]) == expected then return redis.call('del', KEYS[1]) "
                    + "else return 0 end",
            Long.class
    );

    /** 다른 토큰이 한창 선점 중(PROCESSING:*)이 아닐 때만 삭제한다. */
    private static final DefaultRedisScript<Long> CLEAR_IF_NOT_PROCESSING_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('get', KEYS[1]) "
                    + "if current and string.sub(current, 1, 11) == 'PROCESSING:' then return 0 "
                    + "else return redis.call('del', KEYS[1]) end",
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;

    public boolean claim(String key, String token, String value, Duration ttl) {
        Long result = stringRedisTemplate.execute(
                CLAIM_SCRIPT, List.of(key), token, value, String.valueOf(ttl.getSeconds())
        );
        return isSuccess(result);
    }

    public boolean complete(String key, String token, String value, Duration ttl) {
        Long result = stringRedisTemplate.execute(
                COMPLETE_SCRIPT, List.of(key), token, value, String.valueOf(ttl.getSeconds())
        );
        return isSuccess(result);
    }

    public void release(String key, String token) {
        stringRedisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
    }

    public void clearIfNotProcessing(String key) {
        stringRedisTemplate.execute(CLEAR_IF_NOT_PROCESSING_SCRIPT, List.of(key));
    }

    private boolean isSuccess(Long result) {
        return result != null && result == 1L;
    }
}
