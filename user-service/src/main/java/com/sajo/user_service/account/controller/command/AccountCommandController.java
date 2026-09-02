package com.sajo.user_service.account.controller.command;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.user_service.account.controller.dto.request.AccountCreateRequest;
import com.sajo.user_service.account.controller.dto.response.AccountResponse;
import com.sajo.user_service.account.domain.Account;
import com.sajo.user_service.account.service.command.AccountCreateFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AccountCommandController {

    private final AccountCreateFacade accountCreateFacade;

    @PostMapping("/accounts")
    public ResponseEntity<GeneralResponse<AccountResponse>> createAccount(
            @RequestParam("userId") UUID userId, // TODO: Gateway에서 JWT 검증 후 전달하는 X-User-Id 헤더를 사용하도록 변경
            @Valid @RequestBody AccountCreateRequest request
    ) {
        Account account = accountCreateFacade.createAccount(
                userId, request.appKey(), request.secretKey(), request.accountNo(), request.accountType());
        return GeneralResponse.toResponseEntity(GeneralResponseCode.CREATED, AccountResponse.from(account));
    }

}
