package com.sajo.user_service.account.controller.dto.response;

import com.sajo.user_service.account.domain.Account;

public record AccountOrderInfoResponse(
        String cano,
        String accountProductCode,
        String accountType
) {
    public static AccountOrderInfoResponse from(Account account) {
        String accountNo = account.getAccountNo();
        String cano = accountNo.substring(0, 8);
        String accountProductCode = accountNo.substring(9, 11);
        return new AccountOrderInfoResponse(cano, accountProductCode, account.getAccountType().name());
    }
}
