package com.sajo.market_service.market.service.command;

import com.sajo.market_service.market.domain.MarketStockPrice;
import com.sajo.market_service.market.domain.PriceSource;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.repository.command.MarketStockPriceCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
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
 *
 * <p><b>{@code @Transactional}을 의도적으로 붙이지 않았다(코드 리뷰 반영).</b> 이 메서드는
 * {@link MarketStockPriceCommandRepository#save}(SimpleJpaRepository 자체가 이미
 * {@code @Transactional}) 한 번만 호출하므로, 지금 구조에서는 이 메서드 자체에 트랜잭션을 걸지
 * 않아도 저장 하나하나가 자기 자신만의 독립된 트랜잭션으로 커밋/롤백된다. 만약 이 메서드에
 * {@code @Transactional}을 붙이면, {@code save()}가 던지는 {@link DataIntegrityViolationException}이
 * (아래 catch에서 잡아 false를 반환하며 정상 흐름을 이어가려 해도) 그 시점에 이미 JPA가 물리
 * 트랜잭션을 rollback-only로 마킹해버려서, 트랜잭션 커밋 시점에
 * "Transaction rolled back because it has been marked as rollback-only" 예외로 깨질 수 있다.
 * 그러니 이 클래스에 조회/추가 저장 로직을 덧붙이면서 컨벤션에 맞추려고 무심코
 * {@code @Transactional}을 추가하지 말 것 — 필요해지면 이 catch-and-continue 패턴 자체를 다시
 * 설계해야 한다(예: 별도 트랜잭션 전파 옵션, 또는 예외 캐치 위치 재조정).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketRealtimePriceSnapshotCommandService {

    /**
     * V106 partial unique index. 이 이름으로 걸린 {@link DataIntegrityViolationException}만
     * "같은 분(minute)에 대한 중복 스냅샷"으로 간주해 조용히 건너뛴다. 그 외의 제약(FK, NOT NULL 등)
     * 위반은 실제 데이터 문제일 수 있으므로 WARN 로그를 남기고 그대로 전파한다(코드 리뷰 반영).
     */
    private static final String WEBSOCKET_MINUTE_UNIQUE_INDEX = "uk_market_stock_price_websocket_minute";

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
            if (isWebsocketMinuteUniqueViolation(exception)) {
                log.debug("이미 같은 분에 대한 실시간 시세 스냅샷이 존재해 건너뜁니다. stockId={}", stockId);
                return false;
            }

            log.warn(
                    "실시간 시세 스냅샷 저장 중 예상치 못한 데이터 무결성 제약 위반이 발생했습니다. stockId={}",
                    stockId, exception
            );
            throw exception;
        }
    }

    private boolean isWebsocketMinuteUniqueViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolationException) {
                return WEBSOCKET_MINUTE_UNIQUE_INDEX.equals(constraintViolationException.getConstraintName());
            }

            cause = cause.getCause();
        }

        return false;
    }
}
