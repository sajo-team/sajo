package com.sajo.market_service.market.service.command;

import com.sajo.market_service.market.kafka.dto.MarketQuoteRequestedEvent;
import com.sajo.market_service.market.kafka.producer.MarketQuoteRequestEventProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * 현재가 조회 이력을 Kafka에 비동기로 기록하는 커맨드 서비스(#248).
 *
 * <p>조회 자체는 {@code MarketQuoteQueryService}(Query)가 담당하고, 이 서비스는 그 결과와 무관하게
 * "누가 언제 어떤 종목을 조회했는지"만 부가적으로 기록하는 책임을 분리해서 갖는다. Query 서비스에
 * 이 책임을 섞으면 CQS(Command/Query Separation, CLAUDE.md 3번) 원칙을 어기게 되고, Controller가
 * Kafka Producer(인프라 계층)를 직접 호출하게 해도 계층형 아키텍처(CLAUDE.md 2번)에 어긋나므로
 * 별도의 얇은 Command 서비스로 둔다.</p>
 */
@Service
@RequiredArgsConstructor
public class MarketQuoteRequestLogCommandService {

    private final MarketQuoteRequestEventProducer marketQuoteRequestEventProducer;

    public void recordQuoteRequest(UUID userId, String stockCode) {
        marketQuoteRequestEventProducer.publish(
                MarketQuoteRequestedEvent.of(userId, stockCode, Instant.now())
        );
    }
}
