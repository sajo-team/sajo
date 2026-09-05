package com.sajo.user_service.account.service.command;

import com.sajo.user_service.account.client.KisOAuthClient;
import com.sajo.user_service.account.client.dto.response.KisAccessTokenResponse;
import com.sajo.user_service.account.domain.Account;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.service.query.AccountQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountCreateFacade {

    private final KisOAuthClient kisOAuthClient;
    private final AccountQueryService accountQueryService;
    private final AccountCommandService accountCommandService;
    private final KisTokenCacheCommandService kisTokenCacheCommandService;

    public Account createAccount(
            UUID userId, String appKey, String secretKey, String accountNo, AccountType accountType) {

        // 1. 빠른 사전 중복 체크 - 어차피 실패할 요청이면 외부 API(KIS) 호출을 아낀다
        accountQueryService.validateCreatable(userId, accountNo);

        // 2. appKey/secretKey 유효성 검증 - 트랜잭션 밖에서 실행
        KisAccessTokenResponse kisResponse = kisOAuthClient.getAccessToken(appKey, secretKey, accountType);

        // 3. 최종 재확인 + 저장
        Account account = accountCommandService.createAccount(userId, appKey, secretKey, accountNo, accountType);

        // 4. 저장까지 성공한 경우에만, 검증 시 이미 발급받은 토큰을 캐시에 채워 넣는다
        //    (직후 내부 토큰 조회 API가 KIS를 재호출해 1분당 1회 제한에 걸리는 것을 방지)
        //    캐시 저장 실패해도 예외를 던지지 않고 성공 처리한다.
        try {
            kisTokenCacheCommandService.primeKisAccessTokenCache(userId, kisResponse.access_token());
        } catch (Exception e) {
            log.warn("계좌 생성 시 KIS 토큰 캐시 프라이밍 실패. userId={}", userId, e);
        }

        return account;
    }
}
