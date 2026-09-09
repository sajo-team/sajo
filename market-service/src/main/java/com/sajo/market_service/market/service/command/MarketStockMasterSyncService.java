package com.sajo.market_service.market.service.command;

import com.sajo.market_service.market.client.kis.MarketStockMasterDownloadClient;
import com.sajo.market_service.market.dto.command.MarketStockMasterCommand;
import com.sajo.market_service.market.service.parser.MarketStockMasterParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketStockMasterSyncService {
    private final MarketStockMasterDownloadClient downloadClient;
    private final MarketStockMasterParser parser;
    private final MarketStockMasterCommandService commandService;

    public MarketStockMasterSyncResult sync(int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("종목 마스터 청크 크기는 양수여야 합니다.");
        }
        MarketStockMasterSyncResult result = new MarketStockMasterSyncResult(0, 0, 0, 0);
        Map<String, Integer> failureCounts = new LinkedHashMap<>();
        result = result.plus(syncMarket(downloadClient.downloadKOSPI(), "KOSPI", chunkSize, failureCounts));
        result = result.plus(syncMarket(downloadClient.downloadKOSDAQ(), "KOSDAQ", chunkSize, failureCounts));
        if (!failureCounts.isEmpty()) {
            log.error("종목 마스터 청크 실패 원인 요약: {}", failureCounts);
        }
        return result;
    }

    private MarketStockMasterSyncResult syncMarket(
            byte[] zip,
            String marketType,
            int chunkSize,
            Map<String, Integer> failureCounts
    ) {
        MarketStockMasterParser.ParseResult parseResult = parser.parseWithStats(zip, marketType);
        List<MarketStockMasterParser.ParsedStock> parsed = parseResult.stocks();
        int saved = 0;
        int failed = 0;
        for (int i = 0; i < parsed.size(); i += chunkSize) {
            List<MarketStockMasterCommand> chunk = parsed.subList(i, Math.min(i + chunkSize, parsed.size()))
                    .stream().map(MarketStockMasterParser.ParsedStock::command).toList();
            try {
                saved += commandService.saveMasterStocks(chunk);
            } catch (RuntimeException exception) {
                failed += chunk.size();
                Throwable rootCause = rootCause(exception);
                String failureKey = exception.getClass().getSimpleName() + "/" + rootCause.getClass().getSimpleName();
                int occurrence = failureCounts.merge(failureKey, 1, Integer::sum);
                if (occurrence == 1) {
                    log.error("종목 마스터 청크 저장 실패(원인별 최초): exceptionType={}, causeType={}, reason={}",
                            exception.getClass().getSimpleName(),
                            rootCause.getClass().getSimpleName(),
                            safeReason(rootCause));
                }
            }
        }
        return new MarketStockMasterSyncResult(parsed.size(), saved, parseResult.skippedCount(), failed);
    }

    private static Throwable rootCause(Throwable exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }

    private static String safeReason(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "(메시지 없음)";
        }
        String firstLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        return firstLine.length() > 300 ? firstLine.substring(0, 300) : firstLine;
    }
}
