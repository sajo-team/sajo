package com.sajo.trading_service.trading.domain;

import com.sajo.common.entity.BaseUpdatableEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_trading_limits")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradingLimit extends BaseUpdatableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "daily_max_order_amount", nullable = false)
    private Long dailyMaxOrderAmount;

    @Column(name = "daily_max_order_count", nullable = false)
    private Integer dailyMaxOrderCount;

    @Column(name = "daily_loss_limit_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal dailyLossLimitRate;

    private TradingLimit(
            UUID userId,
            Long dailyMaxOrderAmount,
            Integer dailyMaxOrderCount,
            BigDecimal dailyLossLimitRate
    ){
        this.userId = userId;
        this.dailyMaxOrderAmount = dailyMaxOrderAmount;
        this.dailyMaxOrderCount = dailyMaxOrderCount;
        this.dailyLossLimitRate = dailyLossLimitRate;
    }

    public static TradingLimit create(
            UUID userId,
            Long dailyMaxOrderAmount,
            Integer dailyMaxOrderCount,
            BigDecimal dailyLossLimitRate
    ){
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
        if(dailyMaxOrderAmount == null || dailyMaxOrderAmount <= 0){
            throw new IllegalArgumentException(
                    "일일 최대 주문 금액은 0보다 커야합니다"
            );
        }
        if(dailyMaxOrderCount == null || dailyMaxOrderCount <= 0){
            throw new IllegalArgumentException(
                    "일일 최대 주문 횟수는 0보다 커야합니다"
            );
        }
        if(dailyLossLimitRate == null || dailyLossLimitRate.compareTo(BigDecimal.ZERO) <= 0){
            throw new IllegalArgumentException(
                    "일일 최대 손실 한도는 0보다 커야합니다."
            );
        }
        return new TradingLimit(
                userId,
                dailyMaxOrderAmount,
                dailyMaxOrderCount,
                dailyLossLimitRate
        );
    }
}
