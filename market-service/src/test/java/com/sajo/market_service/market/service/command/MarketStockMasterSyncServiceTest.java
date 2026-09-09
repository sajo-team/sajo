package com.sajo.market_service.market.service.command;

import com.sajo.market_service.market.client.kis.MarketStockMasterDownloadClient;
import com.sajo.market_service.market.service.parser.MarketStockMasterParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketStockMasterSyncServiceTest {
    @Mock MarketStockMasterDownloadClient downloadClient;
    @Mock MarketStockMasterParser parser;
    @Mock MarketStockMasterCommandService commandService;
    @InjectMocks MarketStockMasterSyncService service;

    @Test
    void rejectsNonPositiveChunkSizeBeforeDownloading() {
        assertThatThrownBy(() -> service.sync(0)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(downloadClient, parser, commandService);
    }

    @Test
    void savesParsedDataInChunks() {
        when(downloadClient.downloadKOSPI()).thenReturn(new byte[]{1});
        when(downloadClient.downloadKOSDAQ()).thenReturn(new byte[]{2});
        when(parser.parseWithStats(any(), anyString()))
                .thenReturn(new MarketStockMasterParser.ParseResult(List.of(), 2));

        MarketStockMasterSyncResult result = service.sync(10);

        verify(parser).parseWithStats(new byte[]{1}, "KOSPI");
        verify(parser).parseWithStats(new byte[]{2}, "KOSDAQ");
        org.assertj.core.api.Assertions.assertThat(result.skippedCount()).isEqualTo(4);
    }

    @Test
    void continuesAfterChunkFailureAndCountsFailedStocks() {
        MarketStockMasterParser.ParsedStock first = parsed("000001");
        MarketStockMasterParser.ParsedStock second = parsed("000002");
        when(downloadClient.downloadKOSPI()).thenReturn(new byte[]{1});
        when(downloadClient.downloadKOSDAQ()).thenReturn(new byte[]{2});
        when(parser.parseWithStats(new byte[]{1}, "KOSPI"))
                .thenReturn(new MarketStockMasterParser.ParseResult(List.of(first, second), 0));
        when(parser.parseWithStats(new byte[]{2}, "KOSDAQ"))
                .thenReturn(new MarketStockMasterParser.ParseResult(List.of(), 0));
        when(commandService.saveMasterStocks(List.of(first.command())))
                .thenThrow(new IllegalStateException("first chunk failed"));
        when(commandService.saveMasterStocks(List.of(second.command()))).thenReturn(1);

        MarketStockMasterSyncResult result = service.sync(1);

        verify(commandService).saveMasterStocks(List.of(first.command()));
        verify(commandService).saveMasterStocks(List.of(second.command()));
        org.assertj.core.api.Assertions.assertThat(result.savedCount()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(result.failedCount()).isEqualTo(1);
    }

    @Test
    void continuesWithKosdaqWhenKospiDownloadFails() {
        when(downloadClient.downloadKOSPI()).thenThrow(new IllegalStateException("KOSPI unavailable"));
        when(downloadClient.downloadKOSDAQ()).thenReturn(new byte[]{2});
        MarketStockMasterParser.ParsedStock stock = parsed("000250");
        when(parser.parseWithStats(new byte[]{2}, "KOSDAQ"))
                .thenReturn(new MarketStockMasterParser.ParseResult(List.of(stock), 3));
        when(commandService.saveMasterStocks(List.of(stock.command()))).thenReturn(1);

        MarketStockMasterSyncResult result = service.sync(10);

        verify(parser, never()).parseWithStats(any(), eq("KOSPI"));
        verify(commandService).saveMasterStocks(List.of(stock.command()));
        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(3);
        assertThat(result.marketFailureCount()).isEqualTo(1);
    }

    @Test
    void continuesWithKosdaqWhenKospiParsingFails() {
        when(downloadClient.downloadKOSPI()).thenReturn(new byte[]{1});
        when(downloadClient.downloadKOSDAQ()).thenReturn(new byte[]{2});
        when(parser.parseWithStats(new byte[]{1}, "KOSPI"))
                .thenThrow(new IllegalArgumentException("malformed KOSPI"));
        MarketStockMasterParser.ParsedStock stock = parsed("000250");
        when(parser.parseWithStats(new byte[]{2}, "KOSDAQ"))
                .thenReturn(new MarketStockMasterParser.ParseResult(List.of(stock), 0));
        when(commandService.saveMasterStocks(List.of(stock.command()))).thenReturn(1);

        MarketStockMasterSyncResult result = service.sync(10);

        verify(commandService).saveMasterStocks(List.of(stock.command()));
        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(result.marketFailureCount()).isEqualTo(1);
    }

    private static MarketStockMasterParser.ParsedStock parsed(String code) {
        return new MarketStockMasterParser.ParsedStock(
                new com.sajo.market_service.market.dto.command.MarketStockMasterCommand(
                        code, "테스트", "KOSPI", "0001", 1L, BigDecimal.ONE));
    }
}
