package com.other.entity;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditingTestController {

    private final TestEntityRepository repository;

    public AuditingTestController(TestEntityRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/audit-test")
    public String create() {
        TestEntity saved = repository.saveAndFlush(new TestEntity("audit-check"));
        return saved.getCreatedBy() == null ? "null" : saved.getCreatedBy().toString();
    }
}
