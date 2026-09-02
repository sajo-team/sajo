package com.sajo.trading_service.ai_risk.domain;

import com.sajo.common.entity.BaseUpdatableEntity;
import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.ai_risk.exception.AiRiskErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

//TODO: Partial Unique Index 적용
//CREATE UNIQUE INDEX uq_ai_risk_analysis_pending
//ON p_ai_risk_analyses (user_id, strategy_id, backtest_id)
//WHERE status = 'PENDING';

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_factors", columnDefinition = "jsonb")
    private List<RiskFactor> riskFactors;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommendations", columnDefinition = "jsonb")
    private List<String> recommendations;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_type", length = 50)
    private AiAnalysisFailureType failureType;

    @Column(name = "failure_message", columnDefinition = "TEXT")
    private String failureMessage;

    private static void validateRequiredFields(UUID userId, UUID strategyId, UUID backtestId){
        if(userId == null){
            throw new BusinessException(AiRiskErrorCode.USER_ID_REQUIRED);
        }

        if(strategyId == null){
            throw new BusinessException(AiRiskErrorCode.STRATEGY_ID_REQUIRED);
        }

        if(backtestId == null){
            throw new BusinessException(AiRiskErrorCode.BACKTEST_ID_REQUIRED);
        }
    }

    public static AiRiskAnalysis create(
            UUID userId,
            UUID strategyId,
            UUID backtestId
    ){
        validateRequiredFields(userId, strategyId, backtestId);

        AiRiskAnalysis analysis = new AiRiskAnalysis();
        analysis.userId = userId;
        analysis.strategyId = strategyId;
        analysis.backtestId = backtestId;
        analysis.status = AiAnalysisStatus.PENDING;

        return analysis;
    }

    public void complete(
            RiskLevel aiRiskLevel,
            String summary,
            List<RiskFactor> riskFactors,
            String reasoning,
            List<String> recommendations
    ){
        this.status = AiAnalysisStatus.COMPLETED;
        this.riskLevel = aiRiskLevel;
        this.summary = summary;
        this.riskFactors = riskFactors;
        this.reasoning = reasoning;
        this.recommendations = recommendations;

        this.failureType = null;
        this.failureMessage = null;
    }

    public void fail(
            AiAnalysisFailureType failureType,
            String failureMessage
    ) {
        this.status = AiAnalysisStatus.FAILED;
        this.failureType = failureType;
        this.failureMessage = failureMessage;
    }
}
