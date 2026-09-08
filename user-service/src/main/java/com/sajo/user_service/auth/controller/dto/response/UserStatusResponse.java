package com.sajo.user_service.auth.controller.dto.response;

import java.util.UUID;

public record UserStatusResponse(
        UUID userId,
        UserStatus status
) {

    public enum UserStatus {
        ACTIVE, WITHDRAWN
    }

    public static UserStatusResponse of(UUID userId, boolean isDeleted) {
        return new UserStatusResponse(userId, isDeleted ? UserStatus.WITHDRAWN : UserStatus.ACTIVE);
    }
}
