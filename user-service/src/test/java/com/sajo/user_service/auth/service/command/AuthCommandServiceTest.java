package com.sajo.user_service.auth.service.command;

import com.sajo.user_service.auth.service.query.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthCommandServiceTest {

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthCommandService authCommandService;

    @BeforeEach
    void setUp() {
        authCommandService = new AuthCommandService(refreshTokenService);
    }

    @Test
    @DisplayName("로그아웃하면 해당 세션의 refresh token만 무효화한다 (다른 기기 세션에는 영향 없음)")
    void logoutRevokesOnlyThatSession() {
        // given
        String sessionId = "session-abc";

        // when
        authCommandService.logout(sessionId);

        // then
        verify(refreshTokenService).revoke(sessionId);
    }
}
