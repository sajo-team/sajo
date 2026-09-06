package com.sajo.trading_service.ai_risk.repository.query;

import com.sajo.trading_service.ai_risk.document.AiAnalysisHistory;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiAnalysisHistoryQueryRepository extends MongoRepository<AiAnalysisHistory, String> {

    Optional<AiAnalysisHistory> findByAnalysisId(UUID analysisId);

    List<AiAnalysisHistory> findAllByPrompt_VersionIn(Collection<String> versions);
}
