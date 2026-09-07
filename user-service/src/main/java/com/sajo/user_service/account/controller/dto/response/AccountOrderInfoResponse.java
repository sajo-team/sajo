package com.sajo.user_service.account.controller.dto.response;

import com.sajo.user_service.account.domain.Account;

public record AccountOrderInfoResponse(
        String cano,
        String accountProductCode,
        String accountType
) {
    public static AccountOrderInfoResponse from(Account account) {
        String cano = account.getCano();
        String accountProductCode = account.getAccountProductCode();

        return new AccountOrderInfoResponse(cano, accountProductCode, account.getAccountType().name());
    }
}
