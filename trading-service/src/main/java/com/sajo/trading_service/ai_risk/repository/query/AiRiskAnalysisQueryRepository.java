package com.sajo.trading_service.ai_risk.repository.query;

import com.sajo.trading_service.ai_risk.domain.AiRiskAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiRiskAnalysisQueryRepository extends JpaRepository<AiRiskAnalysis, UUID> {

    Optional<AiRiskAnalysis> findByIdAndUserId(UUID id, UUID userId);
}
