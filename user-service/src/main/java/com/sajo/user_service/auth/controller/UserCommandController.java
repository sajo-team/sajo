package com.sajo.user_service.auth.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.user_service.auth.controller.dto.request.SignUpRequest;
import com.sajo.user_service.auth.controller.dto.response.UserResponse;
import com.sajo.user_service.auth.domain.User;
import com.sajo.user_service.auth.service.command.UserCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserCommandController {

    private final UserCommandService userCommandService;

    @PostMapping
    public ResponseEntity<GeneralResponse<UserResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        User user = userCommandService.signUp(request);
        return GeneralResponse.toResponseEntity(GeneralResponseCode.CREATED, UserResponse.from(user));
    }
}
