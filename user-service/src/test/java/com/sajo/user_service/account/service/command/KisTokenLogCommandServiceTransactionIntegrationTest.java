package com.sajo.user_service.account.service.command;

import com.sajo.user_service.account.domain.KisTokenType;
import com.sajo.user_service.account.repository.command.KisTokenLogCommandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// userId는 @Column(nullable = false)라 null로 저장을 시도하면 실제 INSERT가 DB에서 거부되고,
// PostgreSQL이 트랜잭션 자체를 abort 상태로 만든다. Mockito로 예외를 던지는 것만으로는 이 abort
// 상태를 재현할 수 없어 실제 DB를 쓰는 풀 컨텍스트 테스트로 검증한다.
@SpringBootTest
class KisTokenLogCommandServiceTransactionIntegrationTest {

    @Autowired
    private KisTokenLogCommandService kisTokenLogCommandService;

    @Autowired
    private KisTokenLogCommandRepository kisTokenLogCommandRepository;

    @Test
    @DisplayName("userId NOT NULL 위반으로 트랜잭션이 abort돼도 호출자에게 예외가 전파되지 않는다")
    void recordSuccess_withDbLevelConstraintViolation_doesNotPropagateException() {
        long countBefore = kisTokenLogCommandRepository.count();

        assertThatCode(() ->
                kisTokenLogCommandService.recordSuccess(UUID.randomUUID(), null, KisTokenType.ACCESS_TOKEN))
                .doesNotThrowAnyException();

        // 실패한 이력은 롤백되어 저장되지 않아야 한다
        assertThat(kisTokenLogCommandRepository.count()).isEqualTo(countBefore);
    }

    @Test
    @DisplayName("이력 저장 실패 이후에도 트랜잭션/커넥션이 정상 상태로 복구되어 다음 호출이 정상 동작한다")
    void recordSuccess_afterPreviousFailure_stillWorksNormally() {
        // 먼저 실패를 한 번 유발한다
        kisTokenLogCommandService.recordSuccess(UUID.randomUUID(), null, KisTokenType.ACCESS_TOKEN);

        // 곧바로 이어서 정상 호출을 시도한다
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        kisTokenLogCommandService.recordSuccess(accountId, userId, KisTokenType.ACCESS_TOKEN);

        assertThat(kisTokenLogCommandRepository.findAll())
                .anyMatch(log -> userId.equals(log.getUserId()) && accountId.equals(log.getAccountId()));
    }
}
