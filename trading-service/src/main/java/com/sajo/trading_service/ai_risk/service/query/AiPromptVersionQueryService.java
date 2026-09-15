package com.sajo.trading_service.ai_risk.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiPromptVersionHistoryResponse;
import com.sajo.trading_service.ai_risk.document.AiAnalysisHistory;
import com.sajo.trading_service.ai_risk.domain.*;
import com.sajo.trading_service.ai_risk.exception.AiRiskErrorCode;
import com.sajo.trading_service.ai_risk.repository.query.AiAnalysisHistoryQueryRepository;
import com.sajo.trading_service.ai_risk.repository.query.AiPromptVersionQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiPromptVersionQueryService {

    private final AiPromptVersionQueryRepository promptVersionQueryRepository;
    private final AiAnalysisHistoryQueryRepository aiAnalysisHistoryQueryRepository;

    private record PromptStatKey(
            AiPromptKey promptKey,
            String version
    ){
    }

    private static class PromptStatistics {

        private long totalCount;
        private long failedCount;

        private final Map<AiAnalysisFailureType, Long> failureTypeCounts = new EnumMap<>(AiAnalysisFailureType.class);

        public void add(AiAnalysisHistory.ResultSnapshot result){
            totalCount++;

            if(result.status() != AiAnalysisStatus.FAILED){
                return;
            }

            failedCount++;

            if(result.failureType() != null){
                failureTypeCounts.merge(
                        result.failureType(),
                        1L,
                        Long::sum
                );
            }
        }
    }

    public AiPromptVersion getActivePrompt(AiPromptKey promptKey){
        return promptVersionQueryRepository.findByPromptKeyAndStatus(promptKey, AiPromptStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(AiRiskErrorCode.AI_ACTIVE_PROMPT_NOT_FOUND));
    }

    public Page<AiPromptVersionHistoryResponse> getPromptVersionHistories(Pageable pageable){
        Page<AiPromptVersion> promptVersions = promptVersionQueryRepository.findAll(pageable);

        if(promptVersions.isEmpty()){
            return promptVersions.map(promptVersion ->
                    AiPromptVersionHistoryResponse.of(
                            promptVersion,
                            0L,
                            0L,
                            Map.of()
                    )
            );
        }

        Map<AiPromptKey, List<String>> versionsByPromptKey = promptVersions.getContent().stream()
                .collect(Collectors.groupingBy(
                        AiPromptVersion::getPromptKey,
                        Collectors.mapping(
                                AiPromptVersion::getVersion,
                                Collectors.toList()
                        )
                ));

        List<AiAnalysisHistory> histories = versionsByPromptKey.entrySet().stream()
                .flatMap(entry -> aiAnalysisHistoryQueryRepository.findAllByPrompt_PromptKeyAndPrompt_VersionIn(entry.getKey(), entry.getValue()).stream()).toList();

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

        Map<PromptStatKey, PromptStatistics> statisticsMap = new HashMap<>();

        for (AiAnalysisHistory history : histories){

            if(history.getPrompt() == null || history.getResult() == null){
                continue;
            }

            PromptStatKey key = new PromptStatKey(
                    history.getPrompt().promptKey(),
                    history.getPrompt().version()
            );

            statisticsMap.computeIfAbsent(key, ignored -> new PromptStatistics())
                    .add(history.getResult());
        }

        return promptVersions.map(promptVersion -> {
            PromptStatKey key = new PromptStatKey(
                    promptVersion.getPromptKey(),
                    promptVersion.getVersion()
            );

            PromptStatistics statistics = statisticsMap.get(key);

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
