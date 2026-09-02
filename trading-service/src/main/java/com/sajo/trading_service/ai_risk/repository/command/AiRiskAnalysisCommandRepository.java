package com.sajo.trading_service.ai_risk.repository.command;

import com.sajo.trading_service.ai_risk.domain.AiRiskAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiRiskAnalysisCommandRepository extends JpaRepository<AiRiskAnalysis, UUID> {

}
