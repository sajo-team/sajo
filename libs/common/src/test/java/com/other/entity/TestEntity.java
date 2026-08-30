package com.other.entity;

import com.sajo.common.entity.BaseUpdatableEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@NoArgsConstructor
public class TestEntity extends BaseUpdatableEntity {

    @Id
    @GeneratedValue
    private UUID id;

    private String name;

    public TestEntity(String name) {
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void changeName(String name) {
        this.name = name;
    }
}
