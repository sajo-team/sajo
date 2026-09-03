package com.sajo.market_service.market.service.command;

import com.sajo.market_service.market.dto.command.MarketStockMasterCommand;
import com.sajo.market_service.market.domain.MarketStock;
import com.sajo.market_service.market.repository.command.MarketStockMasterWriter;
import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.exception.MarketErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 외부에서 받은 종목 기준정보를 검사하고, 같은 종목코드는 마지막 값만 남긴 뒤, Writer에 일괄 저장을 요청하는 클래스
 */
@Service
@RequiredArgsConstructor
public class MarketStockMasterPersistenceService {

    private final MarketStockMasterWriter marketStockMasterWriter;

    @Transactional
    public int upsertMasterStocks(List<MarketStockMasterCommand> stocks) {
        if (stocks == null) {
            throw new BusinessException(MarketErrorCode.INVALID_MARKET_STOCK, "종목 마스터 목록은 필수입니다.");
        }
        //LinkedHashMap : 중복 값이 들어와도 해당 Key의 원래 위치는 유지되고 값만 마지막 데이터로 바뀐다.
        Map<String, MarketStockMasterCommand> commandsByCode = new LinkedHashMap<>();
        for (MarketStockMasterCommand stock : stocks) {
            if (stock == null) {
                throw new BusinessException(MarketErrorCode.INVALID_MARKET_STOCK, "종목 마스터 목록에 빈 항목이 있습니다.");
            }
            //record는 일반적으로 생성 후 내부 값을 변경할 수 없기 때문에 기존 객체의 값을 직접 변경하는 대신 정리된 값으로 새 Command를 만든다.
            MarketStockMasterCommand normalized = new MarketStockMasterCommand(
                    MarketStock.normalizeStockCode(stock.stockCode()),
                    MarketStock.normalizeStockName(stock.stockName()),
                    MarketStock.normalizeMarketType(stock.marketType()),
                    stock.industryCode(),
                    stock.listedShares(),
                    stock.marketCap()
            );
            //입력 목록에 같은 종목코드가 여러 번 있으면 마지막 정보를 최종 정보로 사용한다.
            commandsByCode.put(normalized.stockCode(), normalized);
        }
        //저장할 종목 없음 → Writer 호출 안 함 → 0 반환
        if (commandsByCode.isEmpty()) {
            return 0;
        }
        return marketStockMasterWriter.upsertAll(List.copyOf(commandsByCode.values()));
    }
}
