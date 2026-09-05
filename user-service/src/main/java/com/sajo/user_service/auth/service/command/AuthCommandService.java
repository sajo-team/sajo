package com.sajo.user_service.auth.service.command;

import com.sajo.user_service.auth.service.query.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthCommandService {

    private final RefreshTokenService refreshTokenService;

    // sessionId 기준으로 로그아웃한다 - 다중 기기 로그인 지원: 이 기기(세션)의 refresh
    // token만 무효화되고, 같은 계정으로 로그인된 다른 기기의 세션에는 영향이 없다.
    public void logout(String sessionId) {
        refreshTokenService.revoke(sessionId);
    }
}
