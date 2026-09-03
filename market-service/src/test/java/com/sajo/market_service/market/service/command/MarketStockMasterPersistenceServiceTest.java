package com.sajo.market_service.market.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.dto.command.MarketStockMasterCommand;
import com.sajo.market_service.market.repository.command.MarketStockMasterWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarketStockMasterPersistenceServiceTest {

    @Mock
    private MarketStockMasterWriter marketStockMasterWriter;

    private MarketStockMasterPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new MarketStockMasterPersistenceService(marketStockMasterWriter);
    }

    @Test
    void rejectsNullStockListBeforeWriting() {
        assertThatThrownBy(() -> service.upsertMasterStocks(null))
                .isInstanceOf(BusinessException.class);

        verify(marketStockMasterWriter, never()).upsertAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void returnsZeroForEmptyStockList() {
        assertThat(service.upsertMasterStocks(List.of())).isZero();

        verify(marketStockMasterWriter, never()).upsertAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void rejectsNullElementBeforeAnyWrite() {
        assertThatThrownBy(() -> service.upsertMasterStocks(Arrays.asList(command("005930", "삼성전자"), null)))
                .isInstanceOf(BusinessException.class);

        verify(marketStockMasterWriter, never()).upsertAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void rejectsInvalidMasterFieldsBeforeWriting() {
        assertThatThrownBy(() -> service.upsertMasterStocks(List.of(command("abc", "삼성전자"))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.upsertMasterStocks(List.of(command("005930", " "))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.upsertMasterStocks(List.of(command("005930", "가".repeat(101)))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.upsertMasterStocks(List.of(new MarketStockMasterCommand(
                "005930", "삼성전자", "NASDAQ", "001", 1_000_000L, null))))
                .isInstanceOf(BusinessException.class);

        verify(marketStockMasterWriter, never()).upsertAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void normalizesInputAndKeepsLastDuplicateBeforeBatchUpsert() {
        given(marketStockMasterWriter.upsertAll(org.mockito.ArgumentMatchers.anyList())).willReturn(1);

        int saved = service.upsertMasterStocks(List.of(
                command(" 005930 ", "이전 이름"),
                command("005930", " 삼성전자 ")
        ));

        assertThat(saved).isEqualTo(1);
        ArgumentCaptor<List<MarketStockMasterCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(marketStockMasterWriter).upsertAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(new MarketStockMasterCommand(
                "005930", "삼성전자", "KOSPI", "001", 1_000_000L, null));
    }

    private MarketStockMasterCommand command(String stockCode, String stockName) {
        return new MarketStockMasterCommand(stockCode, stockName, "KOSPI", "001", 1_000_000L, null);
    }
}
