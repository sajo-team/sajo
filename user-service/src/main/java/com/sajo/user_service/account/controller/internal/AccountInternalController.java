package com.sajo.user_service.account.controller.internal;

import com.sajo.user_service.account.controller.dto.response.AccessTokenResponse;
import com.sajo.user_service.account.service.query.AccountKisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1")
public class AccountInternalController {

    private final AccountKisService accountKisService;

    @PostMapping("/accounts/{userId}/token")
    public AccessTokenResponse getToken(@PathVariable UUID userId) {
        return accountKisService.getKisAccessToken(userId);
    }

}
