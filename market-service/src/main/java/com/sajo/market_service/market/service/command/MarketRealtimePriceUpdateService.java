package com.sajo.market_service.market.service.command;

import com.sajo.market_service.market.cache.MarketQuoteCacheKey;
import com.sajo.market_service.market.cache.MarketQuoteCacheLock;
import com.sajo.market_service.market.config.MarketQuoteCacheProperties;
import com.sajo.market_service.market.dto.kis.KisRealtimePriceMessage;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.service.parser.KisRealtimePriceMessageParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * KIS WebSocket 원문을 정규화해 Redis 최신 시세 캐시({@code market:quote:*})를 갱신한다.
 *
 * <p>{@code /quote} REST 조회와 같은 캐시 키·TTL을 공유한다 — 실시간 체결가가 계속 들어오는 동안은
 * 매 tick마다 TTL이 갱신되므로 사실상 만료되지 않고, 연결이 끊기면 마지막 값이 TTL만큼만 남았다가
 * 자연스럽게 사라진다. PostgreSQL에는 쓰지 않는다(매 tick 저장은 MVP 범위 밖 — 1분 주기 스냅샷은
 * {@code MarketRealtimePriceScheduler} 책임).</p>
 *
 * <p>{@code MarketQuoteQueryService}의 REST 경로도 같은 키를 {@link MarketQuoteCacheLock}으로
 * 보호하며 갱신한다. 이 서비스가 락 없이 get→병합→set을 수행하면, REST 경로가 막 갱신한
 * PER/PBR/시가총액 같은 REST 전용 필드를 이 서비스가 읽어둔 오래된 값 기준으로 되돌려 쓰는 경쟁이
 * 생길 수 있어 동일한 락을 짧게 사용한다(코드 리뷰 반영).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketRealtimePriceUpdateService {

    // REST 경로의 lock-ttl(KIS API 호출 시간까지 포함해 기본 30s)과 달리, 이 서비스는 Redis
    // get/set만 수행하므로 훨씬 짧게 잡아도 충분하다. 프로세스가 락을 쥔 채 죽어도 최대 이 시간만
    // 지나면 자동 해제된다.
    private static final Duration LOCK_TTL = Duration.ofSeconds(2);

    private final KisRealtimePriceMessageParser parser;
    private final RedisTemplate<String, QuoteResponse> quoteRedisTemplate;
    private final MarketQuoteCacheProperties cacheProperties;
    private final MarketQuoteCacheLock cacheLock;
    private final Clock clock;

    /**
     * 원문 한 프레임(여러 레코드가 이어붙어 있을 수 있음)을 처리한다. 개별 레코드 실패는 이 메서드
     * 밖(파서)에서 이미 흡수되며, Redis 자체가 실패해도 예외를 던지지 않고 로그만 남긴다 — WebSocket
     * 메시지 처리 스레드가 이 호출 때문에 죽어서는 안 된다.
     */
    public void updateFromRawMessage(String rawPayload) {
        List<KisRealtimePriceMessage> messages = parser.parse(rawPayload);
        for (KisRealtimePriceMessage message : messages) {
            updateOne(message);
        }
    }

    private void updateOne(KisRealtimePriceMessage message) {
        String stockCode = message.stockCode();
        String cacheKey = MarketQuoteCacheKey.of(stockCode);
        String lockToken = UUID.randomUUID().toString();
        try {
            if (!cacheLock.tryLock(stockCode, lockToken, LOCK_TTL)) {
                // REST 경로(MarketQuoteQueryService)가 같은 종목의 캐시를 갱신 중이다. 여기서 굳이
                // 기다리지 않는다 — 실시간 tick은 금방 다시 오므로 이번 한 번 건너뛰어도 다음 tick에서
                // 다시 시도되고, 억지로 대기하면 WebSocket 메시지 처리 스레드가 REST(KIS API 호출 포함)
                // 만큼 오래 걸릴 수 있는 락 대기에 묶이게 된다.
                log.debug("실시간 시세 캐시 갱신을 건너뜁니다(REST 캐시 갱신과 경합). stockCode={}", stockCode);
                return;
            }
            try {
                QuoteResponse previous = quoteRedisTemplate.opsForValue().get(cacheKey);
                QuoteResponse updated = QuoteResponse.fromRealtime(message, previous, Instant.now(clock));
                quoteRedisTemplate.opsForValue().set(cacheKey, updated, cacheProperties.ttl());
            } finally {
                cacheLock.unlock(stockCode, lockToken);
            }
        } catch (DataAccessException exception) {
            log.warn("KIS 실시간 체결가 Redis 반영에 실패했습니다. stockCode={}", stockCode, exception);
        } catch (RuntimeException exception) {
            log.warn("KIS 실시간 체결가 정규화에 실패했습니다. stockCode={}, exceptionType={}",
                    stockCode, exception.getClass().getSimpleName());
        }
    }
}
