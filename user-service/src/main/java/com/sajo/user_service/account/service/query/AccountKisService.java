package com.sajo.user_service.account.service.query;

import com.sajo.user_service.account.client.KisClient;
import com.sajo.user_service.account.client.dto.response.KISAccessTokenResponse;
import com.sajo.user_service.account.controller.dto.response.AccessTokenResponse;
import com.sajo.user_service.account.domain.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountKisService {

    private final AccountQueryService accountQueryService;
    private final KisClient kisClient;

    public AccessTokenResponse getKisAccessToken(UUID userId) {
        Account account = accountQueryService.getAccountByUserId(userId);
        KISAccessTokenResponse accessToken =
                kisClient.getAccessToken(account.getAppKey(), account.getSecretKey(), account.getAccountType());

        return new AccessTokenResponse(accessToken.access_token(), account.getAppKey(), account.getSecretKey());
    }
}
