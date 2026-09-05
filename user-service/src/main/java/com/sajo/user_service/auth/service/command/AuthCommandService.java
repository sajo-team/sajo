package com.sajo.user_service.auth.service.command;

import com.sajo.user_service.auth.service.query.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthCommandService {

    private final RefreshTokenService refreshTokenService;

    public void logout(UUID userId) {
        refreshTokenService.revoke(userId);
    }
}
