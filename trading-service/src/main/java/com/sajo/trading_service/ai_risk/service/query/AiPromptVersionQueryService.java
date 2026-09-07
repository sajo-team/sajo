package com.sajo.trading_service.ai_risk.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.ai_risk.domain.AiPromptKey;
import com.sajo.trading_service.ai_risk.domain.AiPromptStatus;
import com.sajo.trading_service.ai_risk.domain.AiPromptVersion;
import com.sajo.trading_service.ai_risk.exception.AiRiskErrorCode;
import com.sajo.trading_service.ai_risk.repository.query.AiPromptVersionQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiPromptVersionQueryService {

    private final AiPromptVersionQueryRepository promptVersionQueryRepository;

    public AiPromptVersion getActivePrompt(AiPromptKey promptKey){
        return promptVersionQueryRepository.findByPromptKeyAndStatus(promptKey, AiPromptStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(AiRiskErrorCode.AI_ACTIVE_PROMPT_NOT_FOUND));
    }
}
