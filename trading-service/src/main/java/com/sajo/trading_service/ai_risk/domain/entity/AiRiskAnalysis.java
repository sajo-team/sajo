package com.sajo.trading_service.ai_risk.domain.entity;

import com.sajo.common.entity.BaseUpdatableEntity;
import com.sajo.trading_service.ai_risk.domain.enums.AiAnalysisFailureType;
import com.sajo.trading_service.ai_risk.domain.enums.AiAnalysisStatus;
import com.sajo.trading_service.ai_risk.domain.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "p_ai_risk_analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRiskAnalysis extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "strategy_id", nullable = false)
    private UUID strategyId;

    @Column(name = "backtest_id", nullable = false)
    private UUID backtestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AiAnalysisStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 10)
    private RiskLevel riskLevel;

    @Column(columnDefinition = "TEXT")
    private String summary;

    //riskFactors

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    //recommendations

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_type", length = 50)
    private AiAnalysisFailureType failureType;

    @Column(name = "failure_message", columnDefinition = "TEXT")
    private String failureMessage;

    public static AiRiskAnalysis create(
            UUID userId,
            UUID strategyId,
            UUID backtestId
    ){
        AiRiskAnalysis analysis = new AiRiskAnalysis();
        analysis.userId = userId;
        analysis.strategyId = strategyId;
        analysis.backtestId = backtestId;
        analysis.status = AiAnalysisStatus.PENDING;

        return analysis;
    }
}
