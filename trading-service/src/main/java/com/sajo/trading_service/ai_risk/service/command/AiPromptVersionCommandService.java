package com.sajo.trading_service.ai_risk.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.ai_risk.controller.dto.request.AiPromptVersionCreateRequest;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiPromptVersionCreateResponse;
import com.sajo.trading_service.ai_risk.domain.AiPromptKey;
import com.sajo.trading_service.ai_risk.domain.AiPromptStatus;
import com.sajo.trading_service.ai_risk.domain.AiPromptVersion;
import com.sajo.trading_service.ai_risk.exception.AiRiskErrorCode;
import com.sajo.trading_service.ai_risk.repository.command.AiPromptVersionCommandRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiPromptVersionCommandService {

    private final AiPromptVersionCommandRepository promptVersionCommandRepository;

    private static final String ACTIVE_PROMPT_UNIQUE_INDEX = "uq_ai_prompt_active";

    private static final String PROMPT_VERSION_UNIQUE_CONSTRAINT = "uq_ai_prompt_key_version";

    private String generateNextVersion(AiPromptKey promptKey){
        return promptVersionCommandRepository
                .findTopByPromptKeyOrderByCreatedAtDesc(promptKey)
                .map(AiPromptVersion::getVersion)
                .map(this::incrementVersion)
                .orElse("v1");
    }

    private String incrementVersion(String version){
        int current = Integer.parseInt(version.substring(1));
        return "v"+(current+1);
    }

    private boolean isPromptVersionConflict(
            DataIntegrityViolationException exception
    ) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintException) {
                String constraintName = constraintException.getConstraintName();

                return ACTIVE_PROMPT_UNIQUE_INDEX.equals(constraintName)
                        || PROMPT_VERSION_UNIQUE_CONSTRAINT.equals(constraintName);
            }

            cause = cause.getCause();
        }

        return false;
    }

    @Transactional
    public AiPromptVersionCreateResponse create(
            AiPromptVersionCreateRequest request
    ){
        promptVersionCommandRepository.findByPromptKeyAndStatus(
                request.promptKey(),
                AiPromptStatus.ACTIVE
                )
                .ifPresent(AiPromptVersion::retire);

        String nextVersion = generateNextVersion(request.promptKey());

        AiPromptVersion promptVersion = AiPromptVersion.create(
                request.promptKey(),
                nextVersion,
                request.promptContent(),
                request.changeSummary()
        );

        try{
            AiPromptVersion saved = promptVersionCommandRepository.saveAndFlush(promptVersion);

            return AiPromptVersionCreateResponse.from(saved);
        } catch(DataIntegrityViolationException e){

            if (isPromptVersionConflict(e)) {
                throw new BusinessException(
                        AiRiskErrorCode.AI_PROMPT_VERSION_CONFLICT
                );
            }

            throw e;
        }
    }
}
