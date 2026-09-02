package com.sajo.user_service.account.controller.dto.response;

import com.sajo.user_service.account.domain.Account;
import com.sajo.user_service.account.domain.AccountType;

import java.util.UUID;

public record AccountResponse(
        UUID id,
        String accountNo,
        AccountType accountType
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getAccountNo(), account.getAccountType());
    }
}
