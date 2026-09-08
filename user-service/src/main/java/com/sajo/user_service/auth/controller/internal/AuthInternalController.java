package com.sajo.user_service.auth.controller.internal;

import com.sajo.user_service.auth.controller.dto.response.UserStatusResponse;
import com.sajo.user_service.auth.service.query.UserInternalQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// 서비스 간 호출 전용(Gateway를 거치지 않음) - 다른 서비스가 특정 userId의 존재
// 여부/상태를 확인할 때 사용한다.
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1")
public class AuthInternalController {

    private final UserInternalQueryService userInternalQueryService;

    @GetMapping("/users/{userId}")
    public UserStatusResponse getUserStatus(@PathVariable UUID userId) {
        return userInternalQueryService.getUserStatus(userId);
    }
}
