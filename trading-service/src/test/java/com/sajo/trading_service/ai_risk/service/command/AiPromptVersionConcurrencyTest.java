package com.sajo.trading_service.ai_risk.service.command;

import com.sajo.common.config.CommonJpaAuditingAutoConfiguration;
import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.ai_risk.controller.dto.request.AiPromptVersionCreateRequest;
import com.sajo.trading_service.ai_risk.domain.AiPromptKey;
import com.sajo.trading_service.ai_risk.domain.AiPromptStatus;
import com.sajo.trading_service.ai_risk.domain.AiPromptVersion;
import com.sajo.trading_service.ai_risk.repository.command.AiPromptVersionCommandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Tag("ai-risk")
@Testcontainers
@DataJpaTest
@Import({
        CommonJpaAuditingAutoConfiguration.class,
        AiPromptVersionCommandService.class
})
public class AiPromptVersionConcurrencyTest {

    @Container
    static PostgreSQLContainer postgre = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url", postgre::getJdbcUrl);
        registry.add("spring.datasource.username", postgre::getUsername);
        registry.add("spring.datasource.password", postgre::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiPromptVersionCommandService promptVersionCommandService;

    @Autowired
    private AiPromptVersionCommandRepository promptVersionCommandRepository;

    private void assertOnlyExpectedConflicts(List<Throwable> exceptions){
        assertThat(exceptions)
                .allSatisfy(exception ->
                        assertThat(exception)
                                .isInstanceOf(BusinessException.class));
    }

    @Test
    /*
     * @DataJpaTest는 기본적으로 각 테스트를 하나의 트랜잭션으로 실행한다.
     * 동시성 테스트에서는 각 worker thread가 Service의 @Transactional을 통해
     * 독립적인 트랜잭션을 생성해야 하므로 테스트 자체의 트랜잭션은 비활성화한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("동시 프롬프트 등록 시 Partial Unique Index가 ACTIVE 중복 생성을 방지한다")
    void concurrentCreate_shouldKeepSingleActivePrompt() throws InterruptedException {

        // 동일 prompt_key에는 ACTIVE가 하나만 존재하도록
        // 실제 PostgreSQL에 Partial Unique Index를 생성한다.
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX uq_ai_prompt_active
                ON p_ai_prompt_versions (prompt_key)
                WHERE status = 'ACTIVE'
                """);

        //기존 ACTIVE 프롬프트(v1)이 존재하는 상황을 만든다.
        AiPromptVersion initial = AiPromptVersion.create(
                AiPromptKey.RISK_ANALYSIS,
                "v1",
                "기존 프롬프트",
                "최초 등록"
        );

        promptVersionCommandRepository.saveAndFlush(initial);

        int threadCount = 2;

        //실제로 두 요청이 동시에 들어오는 상황을 만들기 위해 스레드 2개를 만든다.
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        /*
         * ready:
         * 두 스레드가 모두 요청을 실행할 준비가 될 때까지 기다린다.
         *
         * start:
         * 준비된 두 스레드를 최대한 같은 시점에 출발시킨다.
         *
         * done:
         * 두 요청이 모두 끝날 때까지 테스트 스레드가 기다리도록 한다.
         */
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        List<Throwable> exceptions = new CopyOnWriteArrayList<>();

        for(int i = 0; i<threadCount; i++){
            int index = i;

            executorService.submit(() -> {
                try{
                    //현재 스레드가 요청 실행 준비를 마쳤음을 알린다.
                    ready.countDown();

                    //두 스레드가 모두 준비된 뒤 start 신호가 올 때까지 기다린다.
                    start.await();

                    AiPromptVersionCreateRequest request = new AiPromptVersionCreateRequest(
                            AiPromptKey.RISK_ANALYSIS,
                            "동시 요청 프롬프트" + index,
                            "동시성 테스트"
                    );

                    /*
                     * 각 스레드가 create()를 호출하면서
                     * 각각 별도의 @Transactional 트랜잭션에서
                     * 버전 조회 → 기존 ACTIVE retire → 신규 ACTIVE 저장을 수행한다.
                     */
                    promptVersionCommandService.create(request);
                } catch (Exception e){
                    exceptions.add(e);
                } finally {
                    //오쳥 처리가 끝났음을 알린다.
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();

        executorService.shutdown();

        List<AiPromptVersion> prompts = promptVersionCommandRepository.findAll();

        prompts.forEach(prompt ->
                System.out.println(
                        "version=" + prompt.getVersion()
                                + ", status=" + prompt.getStatus()
                )
        );

        exceptions.forEach(exception ->
                System.out.println(
                        "exception="
                                + exception.getClass().getSimpleName()
                                + ": "
                                + exception.getMessage()
                )
        );

        long activeCount = prompts.stream()
                .filter(p -> p.getPromptKey() == AiPromptKey.RISK_ANALYSIS)
                .filter(p -> p.getStatus() == AiPromptStatus.ACTIVE)
                .count();

        /*
         * 동일한 promptKey에는 ACTIVE 프롬프트가 하나만 존재해야 한다.
         *
         * 신규 ACTIVE 프롬프트가 2개 생성된다면? -> race condition이 발생!
         */
        assertThat(activeCount).isEqualTo(1);

        //동시 요청이 충돌하지 않고 순차 처리될 수 있으므로 특정 버전 개수나 예외 발생 횟수에는 의존 x
        assertThat(prompts)
                .extracting(AiPromptVersion::getVersion)
                .doesNotHaveDuplicates();

        assertOnlyExpectedConflicts(exceptions);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("최초 프롬프트 동시 등록 시에도 ACTIVE 프롬프트는 하나만 생성된다")
    void concurrentFirstCreate_shouldKeepSingleActivePrompt()
            throws InterruptedException {

        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_prompt_active
            ON p_ai_prompt_versions (prompt_key)
            WHERE status = 'ACTIVE'
            """);

        int threadCount = 2;

        ExecutorService executorService =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        List<Throwable> exceptions = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            int index = i;

            executorService.submit(() -> {
                try {
                    ready.countDown();
                    start.await();

                    AiPromptVersionCreateRequest request =
                            new AiPromptVersionCreateRequest(
                                    AiPromptKey.RISK_ANALYSIS,
                                    "최초 동시 요청 프롬프트 " + index,
                                    "동시성 테스트"
                            );

                    promptVersionCommandService.create(request);

                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();

        executorService.shutdown();

        List<AiPromptVersion> prompts =
                promptVersionCommandRepository.findAll();

        long activeCount = prompts.stream()
                .filter(p -> p.getPromptKey() == AiPromptKey.RISK_ANALYSIS)
                .filter(p -> p.getStatus() == AiPromptStatus.ACTIVE)
                .count();

        assertThat(activeCount).isEqualTo(1);

        assertThat(prompts).isNotEmpty();

        assertThat(prompts)
                .extracting(AiPromptVersion::getVersion)
                .doesNotHaveDuplicates();

        assertOnlyExpectedConflicts(exceptions);
    }
}
