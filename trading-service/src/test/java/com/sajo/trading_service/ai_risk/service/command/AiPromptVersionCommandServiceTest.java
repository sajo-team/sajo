package com.sajo.trading_service.ai_risk.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.ai_risk.controller.dto.request.AiPromptVersionCreateRequest;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiPromptVersionCreateResponse;
import com.sajo.trading_service.ai_risk.domain.AiPromptKey;
import com.sajo.trading_service.ai_risk.domain.AiPromptStatus;
import com.sajo.trading_service.ai_risk.domain.AiPromptVersion;
import com.sajo.trading_service.ai_risk.repository.command.AiPromptVersionCommandRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@Tag("unit")
@Tag("ai-risk")
@ExtendWith(MockitoExtension.class)
class AiPromptVersionCommandServiceTest {

    @Mock
    private AiPromptVersionCommandRepository promptVersionCommandRepository;

    @InjectMocks
    private AiPromptVersionCommandService promptVersionCommandService;

    @Test
    @DisplayName("등록된 프롬프트가 없으면 v1 버전을 ACTIVE 상태로 생성한다")
    void createFirstPromptVersion() {
        AiPromptVersionCreateRequest request =
                new AiPromptVersionCreateRequest(
                        AiPromptKey.RISK_ANALYSIS,
                        "위험 분석 프롬프트",
                        "최초 등록"
                );

        given(promptVersionCommandRepository
                .findTopByPromptKeyOrderByCreatedAtDesc(AiPromptKey.RISK_ANALYSIS))
                .willReturn(Optional.empty());

        given(promptVersionCommandRepository
                .findByPromptKeyAndStatus(
                        AiPromptKey.RISK_ANALYSIS,
                        AiPromptStatus.ACTIVE))
                .willReturn(Optional.empty());

        given(promptVersionCommandRepository.saveAndFlush(any(AiPromptVersion.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        AiPromptVersionCreateResponse response =
                promptVersionCommandService.create(request);

        // then
        assertThat(response.version()).isEqualTo("v1");
        assertThat(response.promptKey()).isEqualTo(AiPromptKey.RISK_ANALYSIS);
        assertThat(response.status()).isEqualTo(AiPromptStatus.ACTIVE);

        verify(promptVersionCommandRepository)
                .saveAndFlush(any(AiPromptVersion.class));

    }

    @Test
    @DisplayName("기존 ACTIVE 프롬프트가 있으면 RETIRED 처리하고 다음 버전을 생성한다")
    void createNextPromptVersion() {
        AiPromptVersion existing = AiPromptVersion.create(
                AiPromptKey.RISK_ANALYSIS,
                "v1",
                "기존 프롬프트",
                "최초 등록"
        );

        AiPromptVersionCreateRequest request =
                new AiPromptVersionCreateRequest(
                        AiPromptKey.RISK_ANALYSIS,
                        "새로운 프롬프트",
                        "위험 분석 기준 변경"
                );

        given(promptVersionCommandRepository
                .findTopByPromptKeyOrderByCreatedAtDesc(AiPromptKey.RISK_ANALYSIS))
                .willReturn(Optional.of(existing));

        given(promptVersionCommandRepository
                .findByPromptKeyAndStatus(
                        AiPromptKey.RISK_ANALYSIS,
                        AiPromptStatus.ACTIVE))
                .willReturn(Optional.of(existing));

        given(promptVersionCommandRepository.saveAndFlush(any(AiPromptVersion.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        AiPromptVersionCreateResponse response =
                promptVersionCommandService.create(request);

        assertThat(existing.getStatus()).isEqualTo(AiPromptStatus.RETIRED);
        assertThat(existing.getRetiredAt()).isNotNull();

        assertThat(response.version()).isEqualTo("v2");
        assertThat(response.status()).isEqualTo(AiPromptStatus.ACTIVE);
    }

    @Test
    @DisplayName("v9 다음 버전은 v10으로 생성한다")
    void incrementVersionFromV9ToV10() {
        AiPromptVersion existing = AiPromptVersion.create(
                AiPromptKey.RISK_ANALYSIS,
                "v9",
                "기존 프롬프트",
                "기존 버전"
        );

        AiPromptVersionCreateRequest request =
                new AiPromptVersionCreateRequest(
                        AiPromptKey.RISK_ANALYSIS,
                        "새 프롬프트",
                        "변경"
                );

        given(promptVersionCommandRepository
                .findTopByPromptKeyOrderByCreatedAtDesc(AiPromptKey.RISK_ANALYSIS))
                .willReturn(Optional.of(existing));

        given(promptVersionCommandRepository
                .findByPromptKeyAndStatus(
                        AiPromptKey.RISK_ANALYSIS,
                        AiPromptStatus.ACTIVE))
                .willReturn(Optional.of(existing));

        given(promptVersionCommandRepository.saveAndFlush(any(AiPromptVersion.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        AiPromptVersionCreateResponse response =
                promptVersionCommandService.create(request);

        assertThat(response.version()).isEqualTo("v10");
    }

    @Test
    @DisplayName("프롬프트 저장 중 데이터 무결성 충돌이 발생하면 비즈니스 예외를 발생시킨다")
    void createPromptVersionConflict() {
        // given
        AiPromptVersionCreateRequest request =
                new AiPromptVersionCreateRequest(
                        AiPromptKey.RISK_ANALYSIS,
                        "위험 분석 프롬프트",
                        "동시 등록"
                );

        given(promptVersionCommandRepository
                .findByPromptKeyAndStatus(
                        AiPromptKey.RISK_ANALYSIS,
                        AiPromptStatus.ACTIVE
                ))
                .willReturn(Optional.empty());

        given(promptVersionCommandRepository
                .findTopByPromptKeyOrderByCreatedAtDesc(
                        AiPromptKey.RISK_ANALYSIS
                ))
                .willReturn(Optional.empty());

        ConstraintViolationException constraintViolationException = new ConstraintViolationException(
                "duplicate active prompt",
                null,
                "uq_ai_prompt_active"
        );

        DataIntegrityViolationException dataIntegrityViolationException =
                new DataIntegrityViolationException(
                        "prompt version conflict",
                        constraintViolationException
                );

        given(promptVersionCommandRepository
                .saveAndFlush(any(AiPromptVersion.class)))
                .willThrow(dataIntegrityViolationException);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> promptVersionCommandService.create(request)
        );

        assertThat(exception.getMessage())
                .isEqualTo("프롬프트 등록 중 충돌이 발생했습니다.");
    }

    @Test
    @DisplayName("프롬프트 충돌과 무관한 데이터 무결성 예외는 그대로 전파한다")
    void createWithUnexpectedDataIntegrityViolation() {
        AiPromptVersionCreateRequest request =
                new AiPromptVersionCreateRequest(
                        AiPromptKey.RISK_ANALYSIS,
                        "위험 분석 프롬프트",
                        "테스트"
                );

        given(promptVersionCommandRepository
                .findByPromptKeyAndStatus(
                        AiPromptKey.RISK_ANALYSIS,
                        AiPromptStatus.ACTIVE
                ))
                .willReturn(Optional.empty());

        given(promptVersionCommandRepository
                .findTopByPromptKeyOrderByCreatedAtDesc(
                        AiPromptKey.RISK_ANALYSIS
                ))
                .willReturn(Optional.empty());

        DataIntegrityViolationException unexpectedException =
                new DataIntegrityViolationException(
                        "unexpected database constraint violation"
                );

        given(promptVersionCommandRepository
                .saveAndFlush(any(AiPromptVersion.class)))
                .willThrow(unexpectedException);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> promptVersionCommandService.create(request)
        );

        assertThat(thrown).isSameAs(unexpectedException);
    }
}