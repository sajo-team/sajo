package com.sajo.trading_service.trading.repository.query;

import com.sajo.common.config.CommonJpaAuditingAutoConfiguration;
import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.domain.enums.AutoTradingDirection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@Import(CommonJpaAuditingAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AutoTradingQueryRepositoryTest {

    @Autowired
    private AutoTradingQueryRepository autoTradingQueryRepository;

    @Test
    @DisplayName("관리자는 userId와 direction, enabled 조건으로 AutoTrading을 조회할 수 있다")
    void findAllForAdminWithConditions() {

        // given
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        AutoTrading autoTrading1 = AutoTrading.create(
                userId1,
                UUID.randomUUID(),
                AutoTradingDirection.SELL_ONLY
        );

        autoTrading1.update(
                true,
                null
        );

        AutoTrading autoTrading2 = AutoTrading.create(
                userId2,
                UUID.randomUUID(),
                AutoTradingDirection.BUY_ONLY
        );

        autoTradingQueryRepository.saveAllAndFlush(
                List.of(
                        autoTrading1,
                        autoTrading2
                )
        );

        // when
        Page<AutoTrading> result =
                autoTradingQueryRepository.findAllForAdmin(
                        userId1,
                        null,
                        AutoTradingDirection.SELL_ONLY,
                        true,
                        PageRequest.of(0, 10)
                );

        // then
        assertThat(result.getTotalElements())
                .isEqualTo(1);

        AutoTrading found =
                result.getContent().get(0);

        assertThat(found.getUserId())
                .isEqualTo(userId1);

        assertThat(found.getDirection())
                .isEqualTo(AutoTradingDirection.SELL_ONLY);

        assertThat(found.getEnabled())
                .isTrue();
    }
}