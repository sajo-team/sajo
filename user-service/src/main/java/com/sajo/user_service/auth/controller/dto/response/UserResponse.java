package com.sajo.user_service.auth.controller.dto.response;

import com.sajo.user_service.auth.domain.User;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String name
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName());
    }
}
