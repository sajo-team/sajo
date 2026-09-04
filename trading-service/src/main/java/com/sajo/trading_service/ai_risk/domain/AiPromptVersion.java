package com.sajo.trading_service.ai_risk.domain;

import com.sajo.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

// TODO: Partial Unique Index 적용
// 동일한 prompt_key에는 하나의 ACTIVE 프롬프트만 존재해야 한다.
// CREATE UNIQUE INDEX uq_ai_prompt_active
// ON p_ai_prompt_versions (prompt_key)
// WHERE status = 'ACTIVE';

@Entity
@Table(
        name = "p_ai_prompt_versions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_ai_prompt_key_version",
                        columnNames = {"prompt_key", "version"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiPromptVersion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "prompt_key", nullable = false, length = 100)
    private AiPromptKey promptKey;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(name = "prompt_content", nullable = false, columnDefinition = "TEXT")
    private String promptContent;

    @Column(name = "change_summary", columnDefinition = "TEXT")
    private String changeSummary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiPromptStatus status;

    @Column(name = "deployed_at")
    private Instant deployedAt; //이 버전이 실제 ACTIVE가 된 시각

    @Column(name = "retired_at")
    private Instant retiredAt; // ACTIVE에서 RETIRED가 된 시각

    public static AiPromptVersion create(
            AiPromptKey promptKey,
            String version,
            String promptContent,
            String changeSummary
    ) {
        AiPromptVersion promptVersion = new AiPromptVersion();
        promptVersion.promptKey = promptKey;
        promptVersion.version = version;
        promptVersion.promptContent = promptContent;
        promptVersion.changeSummary = changeSummary;
        promptVersion.status = AiPromptStatus.ACTIVE;
        promptVersion.deployedAt = Instant.now();

        return promptVersion;
    }

    public void retire(){
        this.status = AiPromptStatus.RETIRED;
        this.retiredAt = Instant.now();
    }
}
