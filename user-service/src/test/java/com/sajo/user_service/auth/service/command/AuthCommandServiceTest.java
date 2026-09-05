package com.sajo.user_service.auth.service.command;

import com.sajo.user_service.auth.service.query.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

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
    @DisplayName("로그아웃하면 해당 사용자의 refresh token을 무효화한다")
    void logoutRevokesRefreshToken() {
        // given
        UUID userId = UUID.randomUUID();

        // when
        authCommandService.logout(userId);

        // then
        verify(refreshTokenService).revoke(userId);
    }
}
