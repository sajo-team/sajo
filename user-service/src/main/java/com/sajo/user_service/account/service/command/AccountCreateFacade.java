package com.sajo.user_service.account.service.command;

import com.sajo.user_service.account.client.kis.KisTrClient;
import com.sajo.user_service.account.client.kis.dto.response.KisAccessTokenResponse;
import com.sajo.user_service.account.domain.Account;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.domain.KisTokenType;
import com.sajo.user_service.account.exception.KisBusinessException;
import com.sajo.user_service.account.service.query.AccountQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountCreateFacade {

    private final AccountCreationKisTokenCacheService accountCreationKisTokenCacheService;
    private final KisTrClient kisTrClient;
    private final AccountQueryService accountQueryService;
    private final AccountCommandService accountCommandService;
    private final KisTokenCacheCommandService kisTokenCacheCommandService;
    private final KisTokenLogCommandService kisTokenLogCommandService;

    public Account createAccount(
            UUID userId, String appKey, String secretKey, String accountNo, AccountType accountType) {

        // 0. accountNo 형식 검증
        Account.validateAccountNoFormat(accountNo);

        // 1. 빠른 사전 중복 체크 - 어차피 실패할 요청이면 외부 API(KIS) 호출을 아낀다
        accountQueryService.validateCreatable(userId, accountNo);

        // 2. appKey/secretKey 유효성 검증 - 트랜잭션 밖에서 실행
        //    이 시점엔 아직 Account가 없어 실패 시 accountId 없이(null) 기록한다
        //    accountNo만 틀려 3번에서 실패한 뒤 바로 재시도하는 경우를 위해 짧게 캐싱된 토큰을 씀
        //    (KIS 접근토큰 발급 1분당 1회 제한에 매 재시도마다 걸리는 것을 방지)
        KisAccessTokenResponse kisResponse;
        try {
            kisResponse = accountCreationKisTokenCacheService.getAccessToken(userId, appKey, secretKey, accountType);
        } catch (KisBusinessException e) {
            kisTokenLogCommandService.recordFail(
                    null, userId, KisTokenType.ACCESS_TOKEN, e.getKisErrorCode(), e.getKisMessage());
            throw e;
        }

        // 3. accountNo가 KIS에 실제로 존재하는 계좌인지 검증
        // 매수가능조회(inquire-psbl-order)를 호출하여 검증을 진행한다.
        kisTrClient.inquireOrderableAmount(
                kisResponse.access_token(),
                appKey,
                secretKey,
                accountNo.substring(0, 8),
                accountNo.substring(9, 11),
                accountType,
                "", // PDNO - 종목 지정 없이 계좌 단위로만 검증
                "", // ORD_UNPR
                "00" // ORD_DVSN - 임의값
        );

        // 4. 최종 재확인+ 저장
        Account account = accountCommandService.createAccount(userId, appKey, secretKey, accountNo, accountType);

        // 5. 이 시점에 KIS 발급 자체는 이미 성공했으므로, 이력부터 남김
        kisTokenLogCommandService.recordSuccess(account.getId(), userId, KisTokenType.ACCESS_TOKEN);

        // 6. 검증 시 이미 발급받은 토큰을 캐시에 채워 넣는다
        //    (직후 내부 토큰 조회 API가 KIS를 재호출해 1분당 1회 제한에 걸리는 것을 방지)
        //    캐시 저장 실패해도 예외를 던지지 않고 성공 처리한다.
        try {
            kisTokenCacheCommandService.primeKisAccessTokenCache(
                    account.getId(), kisResponse.access_token(), kisResponse.expires_in());
        } catch (Exception e) {
            log.warn("계좌 생성 시 KIS 토큰 캐시 프라이밍 실패. userId={}", userId, e);
        }

        return account;
    }
}
