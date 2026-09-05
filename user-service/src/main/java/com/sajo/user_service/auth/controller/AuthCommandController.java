package com.sajo.user_service.auth.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.user_service.auth.service.command.AuthCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthCommandController {

    private final AuthCommandService authCommandService;

    @PostMapping("/logout")
    public ResponseEntity<GeneralResponse<Void>> logout(@RequestHeader("X-User-Id") UUID userId) {
        authCommandService.logout(userId);
        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, null);
    }
}
