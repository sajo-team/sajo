package com.sajo.trading_service.trading.client.dto.response;

import com.sajo.trading_service.trading.domain.enums.AccountType;

public record AccountOrderInfoResponse(
        String cano,
        String accountProductCode,
        AccountType accountType
) {
}