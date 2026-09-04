package com.sajo.trading_service.ai_risk.service.command;

import com.sajo.trading_service.ai_risk.controller.dto.request.AiPromptVersionCreateRequest;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiPromptVersionCreateResponse;
import com.sajo.trading_service.ai_risk.domain.AiPromptKey;
import com.sajo.trading_service.ai_risk.domain.AiPromptStatus;
import com.sajo.trading_service.ai_risk.domain.AiPromptVersion;
import com.sajo.trading_service.ai_risk.repository.command.AiPromptVersionCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiPromptVersionCommandService {

    private final AiPromptVersionCommandRepository promptVersionCommandRepository;

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

    @Transactional
    public AiPromptVersionCreateResponse create(
            AiPromptVersionCreateRequest request
    ){
        String nextVersion = generateNextVersion(request.promptKey());

        promptVersionCommandRepository.findByPromptKeyAndStatus(
                request.promptKey(),
                AiPromptStatus.ACTIVE
                )
                .ifPresent(AiPromptVersion::retire);

        AiPromptVersion promptVersion = AiPromptVersion.create(
                request.promptKey(),
                nextVersion,
                request.promptContent(),
                request.changeSummary()
        );

        AiPromptVersion saved = promptVersionCommandRepository.save(promptVersion);

        return AiPromptVersionCreateResponse.from(saved);
    }
}
