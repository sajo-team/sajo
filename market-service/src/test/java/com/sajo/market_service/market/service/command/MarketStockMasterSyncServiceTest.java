package com.sajo.market_service.market.service.command;

import com.sajo.market_service.market.client.kis.MarketStockMasterDownloadClient;
import com.sajo.market_service.market.service.parser.MarketStockMasterParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
}
