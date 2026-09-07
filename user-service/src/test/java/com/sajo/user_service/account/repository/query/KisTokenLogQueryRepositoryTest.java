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
import static org.assertj.core.api.Assertions.tuple;

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
    @DisplayName("사용자+토큰타입 조합별로 최신 이벤트 1건씩만 반환한다 - 한쪽 타입의 최신 이벤트가 다른쪽을 가리지 않는다")
    void findLatestPerUserReturnsOnePerTokenType() throws InterruptedException {
        // given
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // 접근토큰: 실패 후 성공으로 복구된 히스토리
        saveLog(accountId, userId, EventType.TOKEN_ISSUE_FAILED, KisTokenType.ACCESS_TOKEN);
        KisTokenLog latestAccessToken =
                saveLog(accountId, userId, EventType.TOKEN_ISSUE_SUCCESS, KisTokenType.ACCESS_TOKEN);

        // 접속키: 그 사이에 발급 실패 (접근토큰 성공보다 나중)
        KisTokenLog latestApprovalKey =
                saveLog(accountId, userId, EventType.TOKEN_ISSUE_FAILED, KisTokenType.APPROVAL_KEY);

        // when
        Page<KisTokenLog> page = kisTokenLogQueryRepository.findLatestPerUser(PageRequest.of(0, 10));

        // then - 접속키의 더 최신 실패가 접근토큰의 최신 성공을 가리지 않고, 둘 다 각자의 최신 상태로 남아있어야 한다
        assertThat(page.getContent())
                .extracting(KisTokenLog::getId)
                .containsExactlyInAnyOrder(latestAccessToken.getId(), latestApprovalKey.getId());
        assertThat(page.getContent())
                .extracting(KisTokenLog::getTokenType, KisTokenLog::getEventType)
                .containsExactlyInAnyOrder(
                        tuple(KisTokenType.ACCESS_TOKEN, EventType.TOKEN_ISSUE_SUCCESS),
                        tuple(KisTokenType.APPROVAL_KEY, EventType.TOKEN_ISSUE_FAILED)
                );
    }

    @Test
    @DisplayName("createdAt이 동시각으로 겹쳐도 그룹당 정확히 1건만 반환한다 (id로 tie-break)")
    void findLatestPerUserReturnsExactlyOnePerGroupOnTie() {
        // given - 같은 순간 저장된 것처럼 만들기 위해 sleep 없이 연속 저장
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        KisTokenLog first = kisTokenLogQueryRepository.saveAndFlush(
                KisTokenLog.createTokenLog(
                        accountId, userId, EventType.TOKEN_ISSUE_SUCCESS, KisTokenType.ACCESS_TOKEN, null, null));
        KisTokenLog second = kisTokenLogQueryRepository.saveAndFlush(
                KisTokenLog.createTokenLog(
                        accountId, userId, EventType.TOKEN_ISSUE_FAILED, KisTokenType.ACCESS_TOKEN, "E1", "실패"));

        // when
        Page<KisTokenLog> page = kisTokenLogQueryRepository.findLatestPerUser(PageRequest.of(0, 10));

        // then - createdAt이 같더라도(디비 정밀도상 같은 값일 수 있음) 정확히 1건만 반환되어야 한다
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getId()).isIn(first.getId(), second.getId());
    }

    @Test
    @DisplayName("여러 사용자가 있으면 사용자별로 각각 최신 1건씩 반환한다")
    void findLatestPerUserReturnsOnePerUser() throws InterruptedException {
        // given
        UUID accountId1 = UUID.randomUUID();
        UUID userId1 = UUID.randomUUID();
        UUID accountId2 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        saveLog(accountId1, userId1, EventType.TOKEN_ISSUE_SUCCESS, KisTokenType.ACCESS_TOKEN);
        KisTokenLog latestUser1 =
                saveLog(accountId1, userId1, EventType.TOKEN_ISSUE_FAILED, KisTokenType.ACCESS_TOKEN);
        KisTokenLog latestUser2 =
                saveLog(accountId2, userId2, EventType.TOKEN_ISSUE_SUCCESS, KisTokenType.ACCESS_TOKEN);

        // when
        Page<KisTokenLog> page = kisTokenLogQueryRepository.findLatestPerUser(PageRequest.of(0, 10));

        // then
        assertThat(page.getContent())
                .extracting(KisTokenLog::getId)
                .containsExactlyInAnyOrder(latestUser1.getId(), latestUser2.getId());
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
