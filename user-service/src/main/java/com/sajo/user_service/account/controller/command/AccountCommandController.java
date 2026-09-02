package com.sajo.user_service.account.controller.command;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.user_service.account.controller.dto.request.AccountCreateRequest;
import com.sajo.user_service.account.controller.dto.response.AccountResponse;
import com.sajo.user_service.account.domain.Account;
import com.sajo.user_service.account.service.command.AccountCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController("/api/v1")
@RequiredArgsConstructor
public class AccountCommandController {

    private final AccountCommandService accountCommandService;

    @PostMapping("/accounts")
    public ResponseEntity<GeneralResponse<AccountResponse>> createAccount(
            @RequestHeader("X-User-Id") UUID userId,
            AccountCreateRequest request
    ) {
        Account account = accountCommandService.createAccount(userId, request);
        return GeneralResponse.toResponseEntity(GeneralResponseCode.CREATED, AccountResponse.from(account));
    }

}
