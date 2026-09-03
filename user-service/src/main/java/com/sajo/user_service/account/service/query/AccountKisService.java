package com.sajo.user_service.account.service.query;

import com.sajo.user_service.account.client.KisClient;
import com.sajo.user_service.account.client.dto.response.KisAccessTokenResponse;
import com.sajo.user_service.account.controller.dto.response.AccessTokenResponse;
import com.sajo.user_service.account.domain.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountKisService {

    private final AccountQueryService accountQueryService;
    private final KisClient kisClient;


    // ToDo : 캐시 만료 시 kis 요청 방지 위해  분산락 적용
    //  @Cacheable -> RedisTemplate 직접 사용으로 전환하면서,
    //  KIS 응답의 expires_in 기준으로 TTL을 동적으로 계산하도록 같이 개선
    @Cacheable(cacheNames = "kis-access-token", key = "#userId", sync = true)
    public AccessTokenResponse getKisAccessToken(UUID userId) {
        Account account = accountQueryService.getAccountByUserId(userId);
        KisAccessTokenResponse accessToken =
                kisClient.getAccessToken(account.getAppKey(), account.getSecretKey(), account.getAccountType());

        return new AccessTokenResponse(accessToken.access_token(), account.getAppKey(), account.getSecretKey());
    }

    // 계좌 생성 시 검증차 이미 발급받은 토큰을 그대로 캐시에 채워 넣는다
    @CachePut(cacheNames = "kis-access-token", key = "#userId")
    public AccessTokenResponse primeKisAccessTokenCache(UUID userId, AccessTokenResponse response) {
        return response;
    }
}
