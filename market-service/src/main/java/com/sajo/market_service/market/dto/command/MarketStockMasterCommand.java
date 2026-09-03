package com.sajo.market_service.market.dto.command;

import java.math.BigDecimal;

/**
 * 종목정보파일 등 외부 마스터 데이터를 검증한 뒤 Command 계층으로 전달하는 내부 DTO.
 */
public record MarketStockMasterCommand(
        String stockCode,
        String stockName,
        String marketType,
        String industryCode,
        Long listedShares,
        BigDecimal marketCap
) {
}
