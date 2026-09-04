package com.sajo.user_service.account.service.query;

import com.sajo.user_service.account.client.KisClient;
import com.sajo.user_service.account.client.dto.response.KisBalanceResponse;
import com.sajo.user_service.account.controller.dto.response.AccessTokenResponse;
import com.sajo.user_service.account.controller.dto.response.AccountDepositResponse;
import com.sajo.user_service.account.controller.dto.response.ApprovalKeyResponse;
import com.sajo.user_service.account.domain.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountKisQueryService {

    private final AccountQueryService accountQueryService;
    private final KisTokenCacheQueryService kisTokenCacheQueryService;
    private final KisClient client;

    // 예수금 조회
    public AccountDepositResponse getDeposit(UUID userId) {
        Account account = accountQueryService.getAccountByUserId(userId);

        String token = kisTokenCacheQueryService.getAccessToken(
                userId,
                account.getAppKey(),
                account.getSecretKey(),
                account.getAccountType()
        );

        KisBalanceResponse kisBalanceResponse = client.inquireBalance(
                token,
                account.getAppKey(),
                account.getSecretKey(),
                account.getCano(),
                account.getAccountProductCode(),
                account.getAccountType()
        );

        return AccountDepositResponse.from(kisBalanceResponse.output2().getFirst(), Instant.now());

    }

    // kis access token 발급
    public AccessTokenResponse getKisAccessToken(UUID userId) {
        Account account = accountQueryService.getAccountByUserId(userId);
        String accessToken = kisTokenCacheQueryService.getAccessToken(
                userId, account.getAppKey(), account.getSecretKey(), account.getAccountType());

        return new AccessTokenResponse(accessToken, account.getAppKey(), account.getSecretKey());
    }

    // kis approval key 발급
    public ApprovalKeyResponse getKisApprovalKey(UUID userId) {
        Account account = accountQueryService.getAccountByUserId(userId);
        String approvalKey = kisTokenCacheQueryService.getApprovalKey(
                userId, account.getAppKey(), account.getSecretKey(), account.getAccountType());

        return new ApprovalKeyResponse(approvalKey);
    }
}
