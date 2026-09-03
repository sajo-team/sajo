package com.sajo.trading_service.ai_risk.repository.command;

import com.sajo.trading_service.ai_risk.document.AiAnalysisHistory;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiAnalysisHistoryCommandRepository extends MongoRepository<AiAnalysisHistory, String> {

    Optional<AiAnalysisHistory> findByAnalysisId(UUID analysisId);
}
