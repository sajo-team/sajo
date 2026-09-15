package com.sajo.market_service.market.service.command;

import com.sajo.market_service.market.domain.MarketStockPrice;
import com.sajo.market_service.market.domain.PriceSource;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.repository.command.MarketStockPriceCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * {@code MarketRealtimePriceScheduler}가 결정한 "구독 종목의 이번 분(minute) Redis 최신값"을
 * {@code source=WEBSOCKET}으로 저장하는 책임만 갖는다. 기존 {@code MarketStockPriceCommandService}
 * (일별 시세, KIS 호출 포함)와는 저장 대상·트리거가 달라 별도 서비스로 분리했다 — Scheduler는 대상
 * 종목 결정과 이 서비스 호출만 담당하고, 엔티티 생성/저장/중복 처리는 여기 둔다(CLAUDE.md 3장,
 * 코드 리뷰 반영).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketRealtimePriceSnapshotCommandService {

    private final MarketStockPriceCommandRepository marketStockPriceCommandRepository;

    /**
     * @return 실제로 저장됐으면 true, 같은 분(minute)에 대한 스냅샷이 이미 있어 건너뛰었으면 false.
     * 후자는 V106 partial unique index({@code uk_market_stock_price_websocket_minute})가 걸려
     * {@link DataIntegrityViolationException}으로 감지되며, 단일 인스턴스 운영을 전제로 하되
     * 재시도/재기동 등으로 겹치는 경우를 대비한 방어다.
     */
    public boolean saveWebsocketSnapshot(UUID stockId, LocalDate date, LocalTime time, QuoteResponse quote) {
        try {
            MarketStockPrice price = MarketStockPrice.create(
                    stockId,
                    date,
                    time,
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
            log.debug("이미 같은 분에 대한 실시간 시세 스냅샷이 존재해 건너뜁니다. stockId={}", stockId);
            return false;
        }
    }
}
