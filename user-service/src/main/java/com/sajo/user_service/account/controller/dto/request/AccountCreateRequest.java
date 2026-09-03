package com.sajo.user_service.account.controller.dto.request;

import com.sajo.user_service.account.domain.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AccountCreateRequest(

        @NotBlank
        String appKey,

        @NotBlank
        String secretKey,

        @NotBlank
        String accountNo,

        @NotNull
        AccountType accountType
) {
}
