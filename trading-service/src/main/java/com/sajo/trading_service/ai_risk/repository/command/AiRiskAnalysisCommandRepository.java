package com.sajo.trading_service.ai_risk.repository.command;

import com.sajo.trading_service.ai_risk.domain.AiAnalysisStatus;
import com.sajo.trading_service.ai_risk.domain.AiRiskAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiRiskAnalysisCommandRepository extends JpaRepository<AiRiskAnalysis, UUID> {

    Optional<AiRiskAnalysis> findByUserIdAndStrategyIdAndBacktestIdAndStatus(
            UUID userId,
            UUID strategyId,
            UUID backtestId,
            AiAnalysisStatus status
    );
}
