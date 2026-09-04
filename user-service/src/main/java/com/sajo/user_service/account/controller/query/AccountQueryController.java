package com.sajo.user_service.account.controller.query;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.user_service.account.controller.dto.response.AccountDepositResponse;
import com.sajo.user_service.account.controller.dto.response.AccountHoldingsResponse;
import com.sajo.user_service.account.service.query.AccountKisQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class AccountQueryController {
    private final AccountKisQueryService accountKisQueryService;

    @GetMapping("/accounts/me/deposit")
    public ResponseEntity<GeneralResponse<AccountDepositResponse>> getDeposit(
            @RequestParam UUID userId //Todo: 인증 구현 후 수정

    ) {
        AccountDepositResponse response = accountKisQueryService.getDeposit(userId);
        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, response);
    }

    @GetMapping("/accounts/me/holdings")
    public ResponseEntity<GeneralResponse<AccountHoldingsResponse>> getHoldings(
            @RequestParam UUID userId, //Todo: 인증 구현 후 수정
            @RequestParam(required = false) String ctxAreaFk100,
            @RequestParam(required = false) String ctxAreaNk100
    ) {
        AccountHoldingsResponse response = accountKisQueryService.getHoldings(userId, ctxAreaFk100, ctxAreaNk100);
        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, response);
    }
}
