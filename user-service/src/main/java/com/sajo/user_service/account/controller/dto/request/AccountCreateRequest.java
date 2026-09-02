package com.sajo.user_service.account.controller.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AccountCreateRequest(

        @NotBlank
        String appKey,

        @NotBlank
        String secretKey,

        @NotBlank
        String accountNo
) {
}
