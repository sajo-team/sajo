package com.sajo.trading_service.trading.domain;

import com.sajo.common.entity.BaseUpdatableEntity;
import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "p_auto_tradings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AutoTrading extends BaseUpdatableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "strategy_id", nullable = false)
    private UUID strategyId;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    private AutoTrading(
            UUID userId,
            UUID strategyId
    ){
        this.userId = userId;
        this.strategyId = strategyId;
        enabled = true;
    }

    public static AutoTrading create(
            UUID userId,
            UUID strategyId
    ) {
        if (userId == null) {
            throw new BusinessException(TradingErrorCode.INVALID_AUTO_TRADING, "사용자 ID는 필수입니다.");
        }

        if (strategyId == null) {
            throw new BusinessException(TradingErrorCode.INVALID_AUTO_TRADING, "전략 ID는 필수입니다.");
        }

        return new AutoTrading(userId, strategyId);
    }
    public void update(
            Boolean enabled
    ) {
        this.enabled = enabled;
    }
}
