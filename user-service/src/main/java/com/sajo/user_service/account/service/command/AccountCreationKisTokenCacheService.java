package com.sajo.user_service.account.service.command;

import com.sajo.user_service.account.client.kis.KisOAuthClient;
import com.sajo.user_service.account.client.kis.dto.response.KisAccessTokenResponse;
import com.sajo.user_service.account.domain.AccountType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

// 계좌 생성 중 KIS 토큰 발급 결과를 짧게 캐싱한다. accountNo가 틀려 계좌 생성이 실패해도
// appKey/secretKey는 유효했던 경우, 사용자가 accountNo만 고쳐 바로 재시도하면 KIS 접근토큰
// 발급(1분당 1회 제한)에 다시 걸리는 문제를 막기 위함 - AccountCreateFacade에서 자기 자신을
// 호출하면 프록시를 안 타서 @Cacheable이 무시되므로 별도 빈으로 분리했다.
@Service
@RequiredArgsConstructor
public class AccountCreationKisTokenCacheService {

    private final KisOAuthClient kisOAuthClient;

    // 캐시 키에 appKey/secretKey의 해시까지 포함시켜, 재시도 시 자격증명 자체가 바뀌면
    // 자동으로 캐시 미스가 나서 옛 토큰을 잘못 재사용하지 않게 한다(값에 평문 저장은 안 함).
    @Cacheable(
            cacheNames = "account-creation-token",
            key = "#userId + ':' + T(java.util.Objects).hash(#appKey, #secretKey)"
    )
    public KisAccessTokenResponse getAccessToken(
            UUID userId, String appKey, String secretKey, AccountType accountType) {
        return kisOAuthClient.getAccessToken(appKey, secretKey, accountType);
    }

    // 계좌 삭제 시 KIS 쪽에서 해당 appKey/secretKey의 토큰이 폐기(revoke)되므로, 이 캐시에
    // 남아있는 같은 자격증명의 항목도 같이 지운다 - 안 지우면 TTL 끝날 때까지 죽은 토큰을
    // 계속 재사용해 KIS로부터 "유효하지 않은 token"(EGW00121) 오류를 받게 된다.
    @CacheEvict(
            cacheNames = "account-creation-token",
            key = "#userId + ':' + T(java.util.Objects).hash(#appKey, #secretKey)"
    )
    public void evict(UUID userId, String appKey, String secretKey) {
    }
}
