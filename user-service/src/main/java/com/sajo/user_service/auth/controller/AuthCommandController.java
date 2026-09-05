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

    // X-User-Id는 인증된 요청인지 확인하는 용도로 받아둔다(로그 등에도 활용 가능).
    // 실제로 어느 세션을 지목해 무효화할지는 X-Session-Id로 결정한다 - 다중 기기
    // 로그인 지원: 로그아웃은 "지금 이 기기의 세션"만 끝내고, 같은 계정으로 로그인된
    // 다른 기기의 세션에는 영향을 주지 않아야 한다.
    @PostMapping("/logout")
    public ResponseEntity<GeneralResponse<Void>> logout(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-Session-Id") String sessionId
    ) {
        authCommandService.logout(sessionId);
        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, null);
    }
}
