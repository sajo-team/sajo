package com.sajo.user_service.auth.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.user_service.auth.controller.dto.request.LoginRequest;
import com.sajo.user_service.auth.controller.dto.request.RefreshRequest;
import com.sajo.user_service.auth.controller.dto.response.LoginResponse;
import com.sajo.user_service.auth.service.query.AuthQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthQueryController {

    private final AuthQueryService authQueryService;

    @PostMapping("/login")
    public ResponseEntity<GeneralResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authQueryService.login(request);
        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<GeneralResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        LoginResponse response = authQueryService.refresh(request);
        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, response);
    }
}
