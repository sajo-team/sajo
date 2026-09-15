package com.sajo.user_service.auth.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.user_service.auth.controller.dto.response.UserResponse;
import com.sajo.user_service.auth.domain.User;
import com.sajo.user_service.auth.service.query.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserQueryController {

    private final UserQueryService userQueryService;

    @GetMapping("/me")
    public ResponseEntity<GeneralResponse<UserResponse>> getMyInfo(
            @RequestHeader("X-User-Id") UUID userId
    ) {
        User user = userQueryService.getMyInfo(userId);
        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, UserResponse.from(user));
    }
}
