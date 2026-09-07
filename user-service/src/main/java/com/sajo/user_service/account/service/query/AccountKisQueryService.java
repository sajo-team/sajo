package com.sajo.user_service.account.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.client.KisContinuationResult;
import com.sajo.user_service.account.client.KisTrClient;
import com.sajo.user_service.account.client.dto.response.KisBalanceHoldingResponse;
import com.sajo.user_service.account.client.dto.response.KisBalanceResponse;
import com.sajo.user_service.account.client.dto.response.KisOrderableAmountResponse;
import com.sajo.user_service.account.controller.dto.response.AccessTokenResponse;
import com.sajo.user_service.account.controller.dto.response.AccountDepositResponse;
import com.sajo.user_service.account.controller.dto.response.AccountHoldingsResponse;
import com.sajo.user_service.account.controller.dto.response.ApprovalKeyResponse;
import com.sajo.user_service.account.controller.dto.response.OrderableAmountResponse;
import com.sajo.user_service.account.controller.dto.response.SellableQuantityResponse;
import com.sajo.user_service.account.domain.Account;
import com.sajo.user_service.account.exception.AccountErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountKisQueryService {

    // 전체 상장종목 수(코스피+코스닥+코넥스 약 2,863개) 기준, 모의투자 페이지당 20건으로 넉넉히 잡은 상한 - 무한 루프 방지용 안전장치
    private static final int MAX_HOLDINGS_PAGE = 150;

    private final AccountQueryService accountQueryService;
    private final KisTokenCacheQueryService kisTokenCacheQueryService;
    private final KisTrClient kisTrClient;

    // 예수금 조회
    public AccountDepositResponse getDeposit(UUID userId) {
        Account account = accountQueryService.getAccountByUserId(userId);

        String token = kisTokenCacheQueryService.getAccessToken(
                userId,
                account.getAppKey(),
                account.getSecretKey(),
                account.getAccountType()
        );

        KisBalanceResponse kisBalanceResponse = kisTrClient.inquireBalance(
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
            log.warn("KIS 예수금 응답 필드 파싱 실패. userId={}", userId, e);
            throw new BusinessException(AccountErrorCode.KIS_BALANCE_INQUIRY_FAILED, "KIS 응답 필드 파싱에 실패했습니다.");
        }
    }

    // 보유종목 조회 (연속조회)
    public AccountHoldingsResponse getHoldings(UUID userId, String ctxAreaFk100, String ctxAreaNk100) {
        // KIS 연속조회 프로토콜상 둘은 항상 한 쌍으로 다녀야 함 - null과 빈 문자열 모두 "커서 없음"으로 취급
        boolean fk100Blank = ctxAreaFk100 == null || ctxAreaFk100.isBlank();
        boolean nk100Blank = ctxAreaNk100 == null || ctxAreaNk100.isBlank();
        if (fk100Blank != nk100Blank) {
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
        KisContinuationResult<KisBalanceResponse> result = kisTrClient.inquireBalance(
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
            log.warn("KIS 보유종목 응답 필드 파싱 실패. userId={}", userId, e);
            throw new BusinessException(AccountErrorCode.KIS_BALANCE_INQUIRY_FAILED, "KIS 응답 필드 파싱에 실패했습니다.");
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

    // 주문 가능 금액 조회
    public OrderableAmountResponse getOrderableAmount(UUID userId) {
        Account account = accountQueryService.getAccountByUserId(userId);

        String token = kisTokenCacheQueryService.getAccessToken(
                userId,
                account.getAppKey(),
                account.getSecretKey(),
                account.getAccountType()
        );

        KisOrderableAmountResponse response = kisTrClient.inquireOrderableAmount(
                token,
                account.getAppKey(),
                account.getSecretKey(),
                account.getCano(),
                account.getAccountProductCode(),
                account.getAccountType(),
                "", // PDNO - 종목 지정 없이 매수금액만 조회
                "", // ORD_UNPR - PDNO와 함께 공란이면 매수금액만 조회됨
                "00" // ORD_DVSN - 매수금액만 조회할 경우 임의값(00) 입력
        );

        if (response.output() == null) {
            throw new BusinessException(
                    AccountErrorCode.KIS_ORDERABLE_AMOUNT_INQUIRY_FAILED, "KIS 응답의 output이 비어 있습니다.");
        }
        try {
            return OrderableAmountResponse.from(response.output());
        } catch (NumberFormatException | NullPointerException e) {
            log.warn("KIS 매수가능금액 응답 필드 파싱 실패. userId={}", userId, e);
            throw new BusinessException(AccountErrorCode.KIS_ORDERABLE_AMOUNT_INQUIRY_FAILED, "KIS 응답 필드 파싱에 실패했습니다.");
        }
    }

    // 매도 가능 수량 조회 (특정 종목) - inquire-balance를 페이지가 끝날 때까지(hasNext=false) 순회하며 stockCode를 찾음
    // 한투 api 중 매도가능수량조회 모의투자는 지원 하지 않아서 주식 잔고 조회를 통해 매도 가능 수량 조회
    public SellableQuantityResponse getSellableQuantity(UUID userId, String stockCode) {
        Account account = accountQueryService.getAccountByUserId(userId);
        String token = kisTokenCacheQueryService.getAccessToken(
                userId,
                account.getAppKey(),
                account.getSecretKey(),
                account.getAccountType()
        );

        int count = 0;
        String ctxFk100 = null;
        String ctxNk100 = null;
        while (count < MAX_HOLDINGS_PAGE) {
            KisContinuationResult<KisBalanceResponse> result = kisTrClient.inquireBalance(
                    token,
                    account.getAppKey(),
                    account.getSecretKey(),
                    account.getCano(),
                    account.getAccountProductCode(),
                    account.getAccountType(),
                    ctxFk100,
                    ctxNk100
            );

            KisBalanceResponse response = result.body();
            Optional<KisBalanceHoldingResponse> found = response.output1().stream()
                    .filter(holding -> stockCode.equals(holding.pdno()))
                    .findFirst();

            if (found.isPresent()) {
                try {
                    return SellableQuantityResponse.from(found.get());
                } catch (NumberFormatException e) {
                    log.warn("KIS 매도가능수량 응답 필드 파싱 실패. userId={}, stockCode={}", userId, stockCode, e);
                    throw new BusinessException(
                            AccountErrorCode.KIS_BALANCE_INQUIRY_FAILED, "KIS 응답 필드 파싱에 실패했습니다.");
                }
            }

            if (!result.hasNext()) {
                return SellableQuantityResponse.notHeld();
            }

            ctxFk100 = response.ctx_area_fk100();
            ctxNk100 = response.ctx_area_nk100();
            count++;
        }

        // 정상적인 계좌라면 절대 도달하지 않음 (전체 상장종목 수 기준 넉넉히 잡은 안전장치) - KIS 응답 이상 시 무한 루프 방지
        log.warn("보유종목 조회 페이지 상한({})에 도달해 조회를 중단합니다. userId={}, stockCode={}",
                MAX_HOLDINGS_PAGE, userId, stockCode);
        return SellableQuantityResponse.notHeld();
    }
}
