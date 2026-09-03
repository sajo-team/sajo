package com.sajo.trading_service.ai_risk.document;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Document(collection = "p_ai_analysis_histories")
public class AiAnalysisHistory {

    @Id
    private String id;

    @Indexed(unique = true)
    private UUID analysisId;

    private UUID userId;

    private UUID strategyId;

    private UUID backtestId;

    private Map<String, Object> requestSnapshot;

    private PromptSnapshot prompt;

    private ResponseSnapshot response;

    private ValidationSnapshot validation;

    private MetadataSnapshot metadata;

    private Instant createdAt;

    @Builder
    private AiAnalysisHistory(
            UUID analysisId,
            UUID userId,
            UUID strategyId,
            UUID backtestId,
            Map<String, Object> requestSnapshot,
            PromptSnapshot prompt,
            ResponseSnapshot response,
            ValidationSnapshot validation,
            MetadataSnapshot metadata
    ) {
        this.analysisId = analysisId;
        this.userId = userId;
        this.strategyId = strategyId;
        this.backtestId = backtestId;
        this.requestSnapshot = requestSnapshot;
        this.prompt = prompt;
        this.response = response;
        this.validation = validation;
        this.metadata = metadata;
        this.createdAt = Instant.now();
    }

    public record PromptSnapshot(
            String version,
            String content
    ){}

    public record ResponseSnapshot(
            String rawResponse
    ){}

    public record ValidationSnapshot(
            Boolean structureValid,
            Boolean contentValid,
            List<String> errors
    ){}

    public record MetadataSnapshot(
            String model,
            Long latencyMs
    ){}
}
