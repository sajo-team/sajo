package com.sajo.user_service.account.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.client.KisContinuationResult;
import com.sajo.user_service.account.client.KisTrClient;
import com.sajo.user_service.account.client.dto.response.KisBalanceResponse;
import com.sajo.user_service.account.controller.dto.response.AccessTokenResponse;
import com.sajo.user_service.account.controller.dto.response.AccountDepositResponse;
import com.sajo.user_service.account.controller.dto.response.AccountHoldingsResponse;
import com.sajo.user_service.account.controller.dto.response.ApprovalKeyResponse;
import com.sajo.user_service.account.domain.Account;
import com.sajo.user_service.account.exception.AccountErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountKisQueryService {

    private final AccountQueryService accountQueryService;
    private final KisTokenCacheQueryService kisTokenCacheQueryService;
    private final KisTrClient client;

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

        if (kisBalanceResponse.output2().isEmpty()) {
            throw new BusinessException(
                    AccountErrorCode.KIS_BALANCE_INQUIRY_FAILED, "KIS 응답의 output2가 비어 있습니다.");
        }
        try {
            return AccountDepositResponse.from(kisBalanceResponse.output2().getFirst(), Instant.now());
        } catch (NumberFormatException | NullPointerException e) {
            throw new BusinessException(AccountErrorCode.KIS_BALANCE_INQUIRY_FAILED, "KIS 응답 필드 파싱 실패: " + e.getMessage());
        }
    }

    // 보유종목 조회 (연속조회)
    public AccountHoldingsResponse getHoldings(UUID userId, String ctxAreaFk100, String ctxAreaNk100) {
        // KIS 연속조회 프로토콜상 둘은 항상 한 쌍으로 다녀야 함
        if ((ctxAreaFk100 == null) != (ctxAreaNk100 == null)) {
            throw new BusinessException(AccountErrorCode.INVALID_CONTINUATION_CURSOR);
        }

        Account account = accountQueryService.getAccountByUserId(userId);

        // 토큰 조회
        String token = kisTokenCacheQueryService.getAccessToken(
                userId,
                account.getAppKey(),
                account.getSecretKey(),
                account.getAccountType()
        );

        // 주식 잔고 조회 요청
        KisContinuationResult<KisBalanceResponse> result = client.inquireBalance(
                token,
                account.getAppKey(),
                account.getSecretKey(),
                account.getCano(),
                account.getAccountProductCode(),
                account.getAccountType(),
                ctxAreaFk100,
                ctxAreaNk100
        );

        KisBalanceResponse response = result.body();
        String nextCtxAreaFk100 = result.hasNext() ? response.ctx_area_fk100() : null;
        String nextCtxAreaNk100 = result.hasNext() ? response.ctx_area_nk100() : null;

        try {
            return AccountHoldingsResponse.from(
                    response.output1(), result.hasNext(), nextCtxAreaFk100, nextCtxAreaNk100, Instant.now());
        } catch (NumberFormatException | NullPointerException e) {
            throw new BusinessException(AccountErrorCode.KIS_BALANCE_INQUIRY_FAILED, "KIS 응답 필드 파싱 실패: " + e.getMessage());
        }

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
