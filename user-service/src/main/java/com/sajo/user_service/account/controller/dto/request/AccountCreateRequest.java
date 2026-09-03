package com.sajo.user_service.account.controller.dto.request;

import com.sajo.user_service.account.domain.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AccountCreateRequest(

        @NotBlank
        String appKey,

        @NotBlank
        String secretKey,

        @NotBlank
        @Pattern(regexp = "^[0-9]{8}-[0-9]{2}$", message = "계좌번호는 12345678-01 형식이어야 합니다")
        String accountNo,

        @NotNull
        AccountType accountType
) {
}
