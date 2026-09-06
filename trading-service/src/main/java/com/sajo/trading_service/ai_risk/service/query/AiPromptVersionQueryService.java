package com.sajo.trading_service.ai_risk.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiPromptVersionHistoryResponse;
import com.sajo.trading_service.ai_risk.document.AiAnalysisHistory;
import com.sajo.trading_service.ai_risk.domain.*;
import com.sajo.trading_service.ai_risk.exception.AiRiskErrorCode;
import com.sajo.trading_service.ai_risk.repository.query.AiAnalysisHistoryQueryRepository;
import com.sajo.trading_service.ai_risk.repository.query.AiPromptVersionQueryRepository;
import com.sajo.trading_service.ai_risk.repository.query.AiRiskAnalysisQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiPromptVersionQueryService {

    private final AiPromptVersionQueryRepository promptVersionQueryRepository;
    private final AiAnalysisHistoryQueryRepository aiAnalysisHistoryQueryRepository;
    private final AiRiskAnalysisQueryRepository aiRiskAnalysisQueryRepository;

    private static class PromptStatistics {

        private long totalCount;
        private long failedCount;

        private final Map<AiAnalysisFailureType, Long> failureTypeCounts = new EnumMap<>(AiAnalysisFailureType.class);

        public void add(AiRiskAnalysis analysis){
            totalCount++;

            if(analysis.getStatus() != AiAnalysisStatus.FAILED){
                return;
            }

            failedCount++;

            if(analysis.getFailureType() != null){
                failureTypeCounts.merge(
                        analysis.getFailureType(),
                        1L,
                        Long::sum
                );
            }
        }
    }

    @Transactional(readOnly = true)
    public AiPromptVersion getActivePrompt(AiPromptKey promptKey){
        return promptVersionQueryRepository.findByPromptKeyAndStatus(promptKey, AiPromptStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(AiRiskErrorCode.AI_ACTIVE_PROMPT_NOT_FOUND));
    }

    public Page<AiPromptVersionHistoryResponse> getPromptVersionHistories(Pageable pageable){
        Page<AiPromptVersion> promptVersions = promptVersionQueryRepository.findAll(pageable);

        List<String> versions = promptVersions.getContent().stream()
                .map(AiPromptVersion :: getVersion)
                .toList();

        if(versions.isEmpty()){
            return promptVersions.map(promptVersion ->
                    AiPromptVersionHistoryResponse.of(
                            promptVersion,
                            0L,
                            0L,
                            Map.of()
                    )
            );
        }

        List<AiAnalysisHistory> histories = aiAnalysisHistoryQueryRepository.findAllByPrompt_VersionIn(versions);

        if(histories.isEmpty()){
            return promptVersions.map(promptVersion ->
                    AiPromptVersionHistoryResponse.of(
                            promptVersion,
                            0L,
                            0L,
                            Map.of()
                    )
            );
        }

        List<UUID> analysisIds = histories.stream()
                .map(AiAnalysisHistory::getAnalysisId)
                .toList();

        List<AiRiskAnalysis> analyses = aiRiskAnalysisQueryRepository.findAllById(analysisIds);

        Map<UUID, AiRiskAnalysis> analysisMap = analyses.stream()
                .collect(Collectors.toMap(
                        AiRiskAnalysis::getId,
                        Function.identity()
                ));

        Map<String, PromptStatistics> statisticsMap = new HashMap<>();

        for (AiAnalysisHistory history : histories){
            AiRiskAnalysis analysis = analysisMap.get(history.getAnalysisId());

            if(analysis == null || history.getPrompt() == null){
                continue;
            }

            String version = history.getPrompt().version();

            statisticsMap.computeIfAbsent(version, key -> new PromptStatistics())
                    .add(analysis);
        }

        return promptVersions.map(promptVersion -> {
            PromptStatistics statistics = statisticsMap.get(promptVersion.getVersion());

            if(statistics == null){
                return AiPromptVersionHistoryResponse.of(
                        promptVersion,
                        0L,
                        0L,
                        Map.of()
                );
            }

            return AiPromptVersionHistoryResponse.of(
                    promptVersion,
                    statistics.totalCount,
                    statistics.failedCount,
                    Map.copyOf(statistics.failureTypeCounts)
            );
        });
    }
}
