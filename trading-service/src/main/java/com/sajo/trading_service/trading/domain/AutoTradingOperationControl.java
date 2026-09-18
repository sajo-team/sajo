package com.sajo.trading_service.trading.domain;

import com.sajo.common.entity.BaseUpdatableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "p_auto_trading_operation_controls")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AutoTradingOperationControl extends BaseUpdatableEntity {

    public static final UUID GLOBAL_CONTROL_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Id
    private UUID id;

    @Column(name = "suspended", nullable = false)
    private Boolean suspended;

    public void suspend() {
        this.suspended = true;
    }

    public void resume() {
        this.suspended = false;
    }

    public boolean isSuspended() {
        return Boolean.TRUE.equals(this.suspended);
    }
}