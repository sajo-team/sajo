package com.sajo.market_service.market.scheduler;

import com.sajo.market_service.market.cache.MarketQuoteCacheKey;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.repository.query.MarketStockCollectionTarget;
import com.sajo.market_service.market.repository.query.MarketStockQueryRepository;
import com.sajo.market_service.market.service.command.MarketRealtimePriceSnapshotCommandService;
import com.sajo.market_service.market.websocket.KisWebSocketClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
 *
 * <p>대상 종목 결정(Redis 조회, stockCode→stockId 매핑)까지만 이 클래스가 담당하고, 엔티티 생성·저장·
 * 중복 처리는 {@link MarketRealtimePriceSnapshotCommandService}(command 패키지)에 위임한다 —
 * 기존 {@link MarketDailyPriceScheduler}와 같은 관례(Scheduler는 조정만, 저장 로직은 Command
 * 서비스)를 따른다(CLAUDE.md 3장, 코드 리뷰 반영).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "market.websocket", name = "enabled", havingValue = "true")
public class MarketRealtimePriceScheduler {

    private final KisWebSocketClient kisWebSocketClient;
    private final MarketStockQueryRepository marketStockQueryRepository;
    private final MarketRealtimePriceSnapshotCommandService snapshotCommandService;
    private final RedisTemplate<String, QuoteResponse> quoteRedisTemplate;
    private final Clock clock;

    @Scheduled(cron = "${market.websocket.realtime-price-snapshot-cron:0 * * * * *}", zone = "Asia/Seoul")
    public void snapshotRealtimePrices() {
        Set<String> stockCodes = kisWebSocketClient.subscribedStockCodes();
        if (stockCodes.isEmpty()) {
            return;
        }

        // stockCode → stockId 매핑은 종목별로 매분 개별 SELECT를 하지 않고 한 번의 IN 조회로 가져온다
        // (코드 리뷰 반영). 조회 전용이라 CommandRepository가 아닌 QueryRepository를 사용한다.
        // 이 한 번의 배치 조회 자체가 실패하면(예: 순간적인 DB 커넥션 장애) snapshotOne()의 종목별
        // 격리와 달리 이번 tick 전체가 예외로 죽어 모든 종목의 스냅샷이 유실될 수 있으므로, 여기서도
        // 잡아서 이번 tick만 건너뛰고 다음 1분 뒤 재시도되도록 한다(코드 리뷰 반영).
        Map<String, UUID> stockIdsByCode;
        try {
            stockIdsByCode = marketStockQueryRepository.findCollectionTargetsByStockCodes(stockCodes)
                    .stream()
                    .collect(Collectors.toMap(
                            MarketStockCollectionTarget::getStockCode,
                            MarketStockCollectionTarget::getStockId));
        } catch (DataAccessException exception) {
            log.warn("실시간 시세 스냅샷 대상 종목 조회에 실패해 이번 tick을 건너뜁니다. stockCodeCount={}",
                    stockCodes.size(), exception);
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock).withSecond(0).withNano(0);
        int savedCount = 0;
        int skippedCount = 0;
        for (String stockCode : stockCodes) {
            if (snapshotOne(stockCode, stockIdsByCode.get(stockCode), now)) {
                savedCount++;
            } else {
                skippedCount++;
            }
        }
        log.debug("실시간 시세 1분 스냅샷을 완료했습니다. savedCount={}, skippedCount={}", savedCount, skippedCount);
    }

    private boolean snapshotOne(String stockCode, UUID stockId, LocalDateTime now) {
        try {
            QuoteResponse quote = quoteRedisTemplate.opsForValue().get(MarketQuoteCacheKey.of(stockCode));
            if (quote == null || quote.currentPrice() == null) {
                return false;
            }
            if (stockId == null) {
                log.warn("실시간 시세 스냅샷 대상 종목을 찾을 수 없습니다. stockCode={}", stockCode);
                return false;
            }
            return snapshotCommandService.saveWebsocketSnapshot(stockId, now.toLocalDate(), now.toLocalTime(), quote);
        } catch (RuntimeException exception) {
            // DataAccessException(Redis/DB 접근 실패)뿐 아니라, 저장 로직이 나중에 바뀌어 다른
            // RuntimeException(NPE, BusinessException 등)이 나더라도 이 종목 하나만 건너뛰고 나머지
            // 종목 처리는 계속되어야 한다 — 그렇지 않으면 이 for 루프가 이 자리에서 그대로 끝나버려
            // 아직 처리 못 한 나머지 종목의 스냅샷까지 전부 유실된다(바로 위 배치 조회 실패 처리와
            // 같은 종류의 리스크). MarketDailyPriceScheduler.collectStock()도 같은 이유로 종목 단위
            // 루프에서 Exception을 넓게 잡는다(코드 리뷰 반영).
            log.warn("실시간 시세 스냅샷 저장 중 예외가 발생했습니다. stockCode={}, exceptionType={}",
                    stockCode, exception.getClass().getSimpleName(), exception);
            return false;
        }
    }
}
