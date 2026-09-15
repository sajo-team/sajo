package com.sajo.market_service.market.scheduler;

import com.sajo.market_service.market.cache.MarketQuoteCacheKey;
import com.sajo.market_service.market.domain.MarketStock;
import com.sajo.market_service.market.domain.MarketStockPrice;
import com.sajo.market_service.market.domain.PriceSource;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.repository.command.MarketStockCommandRepository;
import com.sajo.market_service.market.repository.command.MarketStockPriceCommandRepository;
import com.sajo.market_service.market.websocket.KisWebSocketClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * 구독 중인 종목들의 Redis 최신 시세를 1분마다 스냅샷해 {@code m_market_stocks_price}에
 * {@code source=WEBSOCKET}으로 남긴다.
 *
 * <p>매 tick을 저장하지 않고 1분 간격으로만 저장하는 이유: 관심 종목이 늘어나도 종목당 하루 최대
 * 390row(장중 6.5시간)로 상한이 정해지고, 분봉 차트 API가 나중에 이 데이터를 그대로 재사용할 수
 * 있어서다. WebSocket 수신 스레드({@link KisWebSocketClient})와는 완전히 분리되어 있어, 여기서 DB
 * 저장이 실패해도 실시간 연결·Redis 갱신에는 영향을 주지 않는다.</p>
 *
 * <p>{@link KisWebSocketClient} 빈 자체가 {@code market.websocket.enabled=true}일 때만 생성되므로,
 * 그 빈을 주입받는 이 스케줄러도 동일한 조건으로 등록해야 disabled 환경에서 컨텍스트 기동이
 * 실패하지 않는다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "market.websocket", name = "enabled", havingValue = "true")
public class MarketRealtimePriceScheduler {

    private final KisWebSocketClient kisWebSocketClient;
    private final MarketStockCommandRepository marketStockCommandRepository;
    private final MarketStockPriceCommandRepository marketStockPriceCommandRepository;
    private final RedisTemplate<String, QuoteResponse> quoteRedisTemplate;
    private final Clock clock;

    @Scheduled(cron = "${market.websocket.realtime-price-snapshot-cron:0 * * * * *}", zone = "Asia/Seoul")
    public void snapshotRealtimePrices() {
        Set<String> stockCodes = kisWebSocketClient.subscribedStockCodes();
        if (stockCodes.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock).withSecond(0).withNano(0);
        int savedCount = 0;
        int skippedCount = 0;
        for (String stockCode : stockCodes) {
            if (snapshotOne(stockCode, now)) {
                savedCount++;
            } else {
                skippedCount++;
            }
        }
        log.debug("실시간 시세 1분 스냅샷을 완료했습니다. savedCount={}, skippedCount={}", savedCount, skippedCount);
    }

    private boolean snapshotOne(String stockCode, LocalDateTime now) {
        try {
            QuoteResponse quote = quoteRedisTemplate.opsForValue().get(MarketQuoteCacheKey.of(stockCode));
            if (quote == null || quote.currentPrice() == null) {
                return false;
            }
            UUID stockId = marketStockCommandRepository.findByStockCode(stockCode)
                    .map(MarketStock::getId)
                    .orElse(null);
            if (stockId == null) {
                log.warn("실시간 시세 스냅샷 대상 종목을 찾을 수 없습니다. stockCode={}", stockCode);
                return false;
            }
            return saveSnapshot(stockId, now, quote);
        } catch (DataAccessException exception) {
            log.warn("실시간 시세 스냅샷 저장 중 Redis/DB 접근에 실패했습니다. stockCode={}", stockCode, exception);
            return false;
        }
    }

    // JpaRepository.save()는 SimpleJpaRepository 자체가 @Transactional이라 별도 트랜잭션 래핑이
    // 필요 없다(같은 빈 안에서 @Transactional 메서드를 직접 호출하면 프록시를 안 거쳐 적용도 안 된다).
    // 반환값(저장 성공 여부)을 snapshotOne()까지 그대로 돌려줘야, 위 snapshotRealtimePrices()의
    // savedCount/skippedCount 통계에서 "같은 분 중복이라 건너뜀"이 "저장됨"으로 잘못 집계되지 않는다
    // (코드 리뷰 반영 — 동작에는 영향 없는 로그 통계 정확도 문제였다).
    private boolean saveSnapshot(UUID stockId, LocalDateTime now, QuoteResponse quote) {
        try {
            MarketStockPrice price = MarketStockPrice.create(
                    stockId,
                    now.toLocalDate(),
                    now.toLocalTime(),
                    quote.currentPrice(),
                    null,
                    quote.openPrice(),
                    quote.highPrice(),
                    quote.lowPrice(),
                    quote.previousClosePrice(),
                    quote.changePrice(),
                    quote.changeRate(),
                    null,
                    quote.accumulatedVolume(),
                    quote.tradeAmount(),
                    null,
                    PriceSource.WEBSOCKET
            );
            marketStockPriceCommandRepository.save(price);
            return true;
        } catch (DataIntegrityViolationException exception) {
            // 같은 분(minute)에 대한 스냅샷이 이미 저장돼 있는 경우다(유니크 제약, V106 마이그레이션).
            // 단일 인스턴스 운영을 전제로 하지만, 재시도/재기동 등으로 겹치는 경우를 대비한 방어다.
            log.debug("이미 같은 분에 대한 실시간 시세 스냅샷이 존재해 건너뜁니다. stockId={}", stockId);
            return false;
        }
    }
}
