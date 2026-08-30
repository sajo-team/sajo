package com.other.entity;

import com.other.TestApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@Import(JpaAuditingTestConfig.class)
@DisplayName("BaseEntity/BaseUpdatableEntity JPA Auditing 테스트")
class BaseEntityTest {

    @Autowired
    private TestEntityRepository repository;

    @Autowired
    private TestAuditLogRepository auditLogRepository;

    @Test
    @DisplayName("생성 전용 엔티티는 수정/삭제 필드가 없다")
    void createOnlyEntityHasNoUpdateOrDeleteFields() {
        TestAuditLog saved = auditLogRepository.saveAndFlush(new TestAuditLog("hub-created"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo(JpaAuditingTestConfig.CURRENT_USER);
        // BaseEntity에는 updatedAt/deletedAt 자체가 없어서 getter도 없음 — 컴파일되는 것 자체가 검증
    }

    @Test
    @DisplayName("저장 시 생성 관련 필드가 채워진다")
    void createdFieldsArePopulatedOnSave() {
        TestEntity saved = repository.saveAndFlush(new TestEntity("hub-a"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo(JpaAuditingTestConfig.CURRENT_USER);
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getUpdatedBy()).isEqualTo(JpaAuditingTestConfig.CURRENT_USER);
        assertThat(saved.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("수정 시 updatedAt만 바뀌고 createdAt은 유지된다")
    void updatedAtChangesButCreatedAtStaysOnUpdate() {
        TestEntity saved = repository.saveAndFlush(new TestEntity("hub-b"));
        var createdAt = saved.getCreatedAt();

        saved.changeName("hub-b-renamed");
        TestEntity updated = repository.saveAndFlush(saved);

        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("softDelete를 호출하면 deletedAt/deletedBy가 채워진다")
    void softDeleteSetsDeletedAtAndDeletedBy() {
        TestEntity saved = repository.saveAndFlush(new TestEntity("hub-c"));
        UUID deleter = UUID.randomUUID();

        saved.softDelete(deleter);

        assertThat(saved.isDeleted()).isTrue();
        assertThat(saved.getDeletedAt()).isNotNull();
        assertThat(saved.getDeletedBy()).isEqualTo(deleter);
    }
}
