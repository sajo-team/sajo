package com.sajo.trading_service.trading.domain;

import com.sajo.common.entity.BaseUpdatableEntity;
import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.domain.enums.AutoTradingDirection;
import com.sajo.trading_service.trading.domain.enums.OrderType;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private AutoTradingDirection direction;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    private AutoTrading(
            UUID userId,
            UUID strategyId,
            AutoTradingDirection direction
    ){
        this.userId = userId;
        this.strategyId = strategyId;
        this.direction = direction;
        enabled = false;
    }

    public static AutoTrading create(
            UUID userId,
            UUID strategyId,
            AutoTradingDirection direction
    ) {
        if (userId == null) {
            throw new BusinessException(TradingErrorCode.INVALID_AUTO_TRADING, "사용자 ID는 필수입니다.");
        }

        if (strategyId == null) {
            throw new BusinessException(TradingErrorCode.INVALID_AUTO_TRADING, "전략 ID는 필수입니다.");
        }

        if (direction == null) {
            throw new BusinessException(TradingErrorCode.INVALID_AUTO_TRADING, "자동매매 주문 방향은 필수입니다.");
        }

        return new AutoTrading(
                userId,
                strategyId,
                direction);
    }
    public void update(
            Boolean enabled,
            AutoTradingDirection direction
    ) {
        if (enabled != null) {
            this.enabled = enabled;
        }
        if (direction != null) {
            this.direction = direction;
        }
    }

    public void validateDirection(OrderType orderType) {
        boolean allowed = switch (direction) {
            case BUY_ONLY -> orderType == OrderType.BUY;
            case SELL_ONLY -> orderType == OrderType.SELL;
            case BOTH -> true;
        };

        if (!allowed) {
            throw new BusinessException(
                    TradingErrorCode.AUTO_TRADING_DIRECTION_NOT_ALLOWED
            );
        }
    }
}
