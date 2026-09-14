package com.sajo.market_service.market.service.command;

import com.sajo.market_service.market.cache.MarketQuoteCacheKey;
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
import java.time.Instant;
import java.util.List;

/**
 * KIS WebSocket 원문을 정규화해 Redis 최신 시세 캐시({@code market:quote:*})를 갱신한다.
 *
 * <p>{@code /quote} REST 조회와 같은 캐시 키·TTL을 공유한다 — 실시간 체결가가 계속 들어오는 동안은
 * 매 tick마다 TTL이 갱신되므로 사실상 만료되지 않고, 연결이 끊기면 마지막 값이 TTL만큼만 남았다가
 * 자연스럽게 사라진다. PostgreSQL에는 쓰지 않는다(매 tick 저장은 MVP 범위 밖 — 1분 주기 스냅샷은
 * {@code MarketRealtimePriceScheduler} 책임).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketRealtimePriceUpdateService {

    private final KisRealtimePriceMessageParser parser;
    private final RedisTemplate<String, QuoteResponse> quoteRedisTemplate;
    private final MarketQuoteCacheProperties cacheProperties;
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
        String cacheKey = MarketQuoteCacheKey.of(message.stockCode());
        try {
            QuoteResponse previous = quoteRedisTemplate.opsForValue().get(cacheKey);
            QuoteResponse updated = QuoteResponse.fromRealtime(message, previous, Instant.now(clock));
            quoteRedisTemplate.opsForValue().set(cacheKey, updated, cacheProperties.ttl());
        } catch (DataAccessException exception) {
            log.warn("KIS 실시간 체결가 Redis 반영에 실패했습니다. stockCode={}", message.stockCode(), exception);
        } catch (RuntimeException exception) {
            log.warn("KIS 실시간 체결가 정규화에 실패했습니다. stockCode={}, exceptionType={}",
                    message.stockCode(), exception.getClass().getSimpleName());
        }
    }
}
