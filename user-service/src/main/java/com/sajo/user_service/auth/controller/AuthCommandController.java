package com.sajo.user_service.auth.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.user_service.auth.controller.dto.request.LoginRequest;
import com.sajo.user_service.auth.controller.dto.request.RefreshRequest;
import com.sajo.user_service.auth.controller.dto.response.LoginResponse;
import com.sajo.user_service.auth.service.command.AuthCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthCommandController {

    private final AuthCommandService authCommandService;

    @PostMapping("/login")
    public ResponseEntity<GeneralResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authCommandService.login(request);
        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<GeneralResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        LoginResponse response = authCommandService.refresh(request);
        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, response);
    }

    // X-User-Id는 인증된 요청인지 확인하는 용도로 받아둔다(로그 등에도 활용 가능).
    // 실제로 어느 세션을 지목해 무효화할지는 X-Session-Id로 결정한다 - 다중 기기
    // 로그인 지원: 로그아웃은 "지금 이 기기의 세션"만 끝내고, 같은 계정으로 로그인된
    // 다른 기기의 세션에는 영향을 주지 않아야 한다.
    //
    // X-Session-Id를 필수(required)로 두지 않는다 - 리뷰 반영: 로그인 시점에 Redis
    // 장애로 fail-open되어 sessionId 없이 access token이 발급된 경우, Gateway가 이
    // 헤더를 null로 세팅하는데 required=true였다면 그 세션은 로그아웃 자체를 정상
    // 요청할 수 없었다(400). authCommandService.logout()이 null/blank를 안전하게
    // no-op으로 처리한다.
    @PostMapping("/logout")
    public ResponseEntity<GeneralResponse<Void>> logout(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId
    ) {
        authCommandService.logout(sessionId);
        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, null);
    }
}
