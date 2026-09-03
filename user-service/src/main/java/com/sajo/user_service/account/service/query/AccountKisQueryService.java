package com.sajo.user_service.account.service.query;

import com.sajo.user_service.account.controller.dto.response.AccessTokenResponse;
import com.sajo.user_service.account.controller.dto.response.ApprovalKeyResponse;
import com.sajo.user_service.account.domain.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountKisQueryService {

    private final AccountQueryService accountQueryService;
    private final KisTokenCacheService kisTokenCacheService;

    public AccessTokenResponse getKisAccessToken(UUID userId) {
        Account account = accountQueryService.getAccountByUserId(userId);
        String accessToken = kisTokenCacheService.getAccessToken(
                userId, account.getAppKey(), account.getSecretKey(), account.getAccountType());

        return new AccessTokenResponse(accessToken, account.getAppKey(), account.getSecretKey());
    }

    public ApprovalKeyResponse getKisApprovalKey(UUID userId) {
        Account account = accountQueryService.getAccountByUserId(userId);
        String approvalKey = kisTokenCacheService.getApprovalKey(
                userId, account.getAppKey(), account.getSecretKey(), account.getAccountType());

        return new ApprovalKeyResponse(approvalKey);
    }
}
