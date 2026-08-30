package com.other.entity;

import com.sajo.common.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@NoArgsConstructor
public class TestAuditLog extends BaseEntity {

    @Id
    @GeneratedValue
    private UUID id;

    private String action;

    public TestAuditLog(String action) {
        this.action = action;
    }

    public UUID getId() {
        return id;
    }

    public String getAction() {
        return action;
    }
}
