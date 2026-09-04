package com.sajo.trading_service.ai_risk.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.ai_risk.domain.AiPromptKey;
import com.sajo.trading_service.ai_risk.domain.AiPromptStatus;
import com.sajo.trading_service.ai_risk.domain.AiPromptVersion;
import com.sajo.trading_service.ai_risk.exception.AiRiskErrorCode;
import com.sajo.trading_service.ai_risk.repository.query.AiPromptVersionQueryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@Tag("unit")
@Tag("ai-risk")
@ExtendWith(MockitoExtension.class)
class AiPromptVersionQueryServiceTest {

    @Mock
    private AiPromptVersionQueryRepository promptVersionQueryRepository;

    @InjectMocks
    private AiPromptVersionQueryService promptVersionQueryService;

    @Test
    @DisplayName("ACTIVE 프롬프트를 조회한다")
    void getActivePrompt(){
        AiPromptVersion promptVersion = AiPromptVersion.create(
                AiPromptKey.RISK_ANALYSIS,
                "v1",
                "위험 분석 프롬프트",
                "최초 등록"
        );

        given(promptVersionQueryRepository.findByPromptKeyAndStatus(
                AiPromptKey.RISK_ANALYSIS,
                AiPromptStatus.ACTIVE
        )).willReturn(Optional.of(promptVersion));

        AiPromptVersion result = promptVersionQueryService.getActivePrompt(
                AiPromptKey.RISK_ANALYSIS
        );

        assertThat(result).isSameAs(promptVersion);
    }

    @Test
    @DisplayName("ACTIVE 프롬프트가 없으면 비즈니스 예외가 발생한다")
    void getActivePromptNotFound(){
        given(promptVersionQueryRepository.findByPromptKeyAndStatus(
                AiPromptKey.RISK_ANALYSIS,
                AiPromptStatus.ACTIVE
        )).willReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> promptVersionQueryService.getActivePrompt(
                        AiPromptKey.RISK_ANALYSIS
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(AiRiskErrorCode.AI_ACTIVE_PROMPT_NOT_FOUND);
    }
}