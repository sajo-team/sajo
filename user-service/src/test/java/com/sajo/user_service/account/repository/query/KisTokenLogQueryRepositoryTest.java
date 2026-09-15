package com.sajo.user_service.account.repository.query;

import com.sajo.common.config.CommonJpaAuditingAutoConfiguration;
import com.sajo.user_service.account.domain.EventType;
import com.sajo.user_service.account.domain.KisTokenLog;
import com.sajo.user_service.account.domain.KisTokenType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@Import(CommonJpaAuditingAutoConfiguration.class)
class KisTokenLogQueryRepositoryTest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private KisTokenLogQueryRepository kisTokenLogQueryRepository;

    private KisTokenLog saveLog(UUID accountId, UUID userId, EventType eventType, KisTokenType tokenType)
            throws InterruptedException {
        KisTokenLog saved = kisTokenLogQueryRepository.saveAndFlush(
                KisTokenLog.createTokenLog(accountId, userId, eventType, tokenType, null, null));
        // createdAt이 @CreatedDate로 자동 채워지는데, 같은 밀리초에 연속 저장되면
        // "최신 1건" 판정에 필요한 시간 순서를 보장할 수 없어 최소 간격을 둔다
        Thread.sleep(5);
        return saved;
    }

    @Test
    @DisplayName("특정 사용자의 이벤트를 최신순으로 페이지네이션하여 조회한다")
    void findByUserIdOrderByCreatedAtDescReturnsNewestFirst() throws InterruptedException {
        // given
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        KisTokenLog first = saveLog(accountId, userId, EventType.TOKEN_ISSUE_SUCCESS, KisTokenType.ACCESS_TOKEN);
        KisTokenLog second = saveLog(accountId, userId, EventType.TOKEN_ISSUE_FAILED, KisTokenType.ACCESS_TOKEN);
        saveLog(UUID.randomUUID(), otherUserId, EventType.TOKEN_ISSUE_SUCCESS, KisTokenType.ACCESS_TOKEN);

        // when
        Page<KisTokenLog> page =
                kisTokenLogQueryRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 10));

        // then
        assertThat(page.getContent())
                .extracting(KisTokenLog::getId)
                .containsExactly(second.getId(), first.getId());
    }
}
