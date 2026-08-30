package com.other.entity;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;
import java.util.UUID;

@TestConfiguration
@EnableJpaAuditing
public class JpaAuditingTestConfig {

    static final UUID CURRENT_USER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> Optional.of(CURRENT_USER);
    }
}
