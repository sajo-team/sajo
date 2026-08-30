package com.sajo.common.entity;

import org.springframework.data.domain.AuditorAware;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

public class RequestHeaderAuditorAware implements AuditorAware<UUID> {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public Optional<UUID> getCurrentAuditor() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return Optional.empty();
        }

        String userId = attributes.getRequest().getHeader(USER_ID_HEADER);
        if (userId == null) {
            return Optional.empty();
        }

        return Optional.of(UUID.fromString(userId));
    }
}
