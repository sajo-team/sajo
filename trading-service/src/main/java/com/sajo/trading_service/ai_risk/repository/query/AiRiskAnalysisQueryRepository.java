package com.sajo.trading_service.ai_risk.repository.query;

import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisStatus;
import com.sajo.trading_service.ai_risk.domain.AiRiskAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiRiskAnalysisQueryRepository extends JpaRepository<AiRiskAnalysis, UUID> {

    Optional<AiRiskAnalysis> findByIdAndUserId(UUID id, UUID userId);

    Page<AiRiskAnalysis> findAllByUserId(UUID userId, Pageable pageable);

    Page<AiRiskAnalysis> findAllByStatus(AiAnalysisStatus status, Pageable pageable);

    Page<AiRiskAnalysis> findAllByStatusAndFailureType(AiAnalysisStatus status, AiAnalysisFailureType failureType, Pageable pageable);
}
