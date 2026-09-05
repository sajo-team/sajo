package com.sajo.trading_service.ai_risk.repository.command;

import com.sajo.trading_service.ai_risk.domain.AiPromptKey;
import com.sajo.trading_service.ai_risk.domain.AiPromptStatus;
import com.sajo.trading_service.ai_risk.domain.AiPromptVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiPromptVersionCommandRepository extends JpaRepository<AiPromptVersion, UUID> {

    Optional<AiPromptVersion> findByPromptKeyAndStatus(
            AiPromptKey promptKey,
            AiPromptStatus status
    );

    Optional<AiPromptVersion> findTopByPromptKeyOrderByCreatedAtDesc(
            AiPromptKey promptKey
    );
}
