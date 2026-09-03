package com.sajo.user_service.account.service.command;

import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AccountKisCommandService {

    // 계좌 생성 시 검증차 이미 발급받은 토큰을 그대로 캐시에 채워 넣는다 (accessToken 문자열만 캐싱)
    @CachePut(cacheNames = "kis-access-token", key = "#userId")
    public String primeKisAccessTokenCache(UUID userId, String accessToken) {
        return accessToken;
    }
}
