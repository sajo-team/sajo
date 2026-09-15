package com.sajo.user_service.account.repository.command;

import com.sajo.user_service.account.domain.EventType;
import com.sajo.user_service.account.domain.KisTokenStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
class KisTokenStatusCommandRepositoryTest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine");

    // ON CONFLICT (user_id, token_type)가 기대는 unique 제약은 엔티티가 아니라
    // V4 마이그레이션 SQL에만 선언돼 있다 - ddl-auto(create-drop 등)로 스키마를 생성하면
    // Hibernate가 엔티티 매핑만으로 테이블을 새로 만들어 이 제약이 빠지고 ON CONFLICT가
    // "no unique or exclusion constraint" 에러로 깨진다. 그래서 ddl-auto는 끄고 실제
    // Flyway 마이그레이션(db/migration)이 스키마를 만들도록 한다.
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // V4 마이그레이션이 user_account 스키마에 테이블을 만들고, 네이티브 upsert 쿼리는
        // 테이블명을 스키마 없이 참조하므로 currentSchema를 맞춰줘야 한다.
        registry.add("spring.datasource.url", () -> {
            String jdbcUrl = postgres.getJdbcUrl();
            String separator = jdbcUrl.contains("?") ? "&" : "?";
            return jdbcUrl + separator + "currentSchema=user_account";
        });
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    private KisTokenStatusCommandRepository kisTokenStatusCommandRepository;

    private KisTokenStatus findStatus(UUID userId) {
        return kisTokenStatusCommandRepository.findAll().stream()
                .filter(status -> status.getUserId().equals(userId))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("같은 user+tokenType에 upsert하면 기존 행을 더 최신 이벤트 내용으로 갱신한다")
    void upsertUpdatesExistingRowWithNewerEvent() {
        // given
        UUID userId = UUID.randomUUID();
        Instant older = Instant.now();
        Instant newer = older.plusSeconds(10);

        kisTokenStatusCommandRepository.upsert(
                UUID.randomUUID(), userId, "ACCESS_TOKEN", "TOKEN_ISSUE_SUCCESS", null, null, older);

        // when - 더 최신 이벤트로 upsert
        kisTokenStatusCommandRepository.upsert(
                UUID.randomUUID(), userId, "ACCESS_TOKEN", "TOKEN_ISSUE_FAILED", "E1", "실패", newer);

        // then
        KisTokenStatus status = findStatus(userId);
        assertThat(status.getEventType()).isEqualTo(EventType.TOKEN_ISSUE_FAILED);
        assertThat(status.getErrorCode()).isEqualTo("E1");
        assertThat(status.getErrorMessage()).isEqualTo("실패");
    }

    @Test
    @DisplayName("더 과거 시점의 이벤트가 뒤늦게 upsert돼도(커밋 순서 역전) 더 최신 상태를 덮어쓰지 않는다")
    void upsertDoesNotRegressToOlderEvent() {
        // given - 최신 이벤트가 먼저 반영됨
        UUID userId = UUID.randomUUID();
        Instant newer = Instant.now();
        Instant older = newer.minusSeconds(10);

        kisTokenStatusCommandRepository.upsert(
                UUID.randomUUID(), userId, "ACCESS_TOKEN", "TOKEN_ISSUE_SUCCESS", null, null, newer);

        // when - 더 과거 시점의 이벤트가 뒤늦게 도착 (동시 요청의 트랜잭션 커밋 순서 역전 재현)
        kisTokenStatusCommandRepository.upsert(
                UUID.randomUUID(), userId, "ACCESS_TOKEN", "TOKEN_ISSUE_FAILED", "E1", "실패", older);

        // then - 상태는 여전히 더 최신이었던 성공 이벤트로 남아있어야 한다
        KisTokenStatus status = findStatus(userId);
        assertThat(status.getEventType()).isEqualTo(EventType.TOKEN_ISSUE_SUCCESS);
        assertThat(status.getErrorCode()).isNull();
        assertThat(status.getErrorMessage()).isNull();
    }
}
