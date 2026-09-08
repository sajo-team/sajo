package com.sajo.market_service.strategy.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.strategy.controller.dto.response.StrategyActivationResponse;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.domain.StrategyStatus;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import com.sajo.market_service.strategy.repository.command.StrategyCommandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StrategyActivationCommandServiceTest {

    @Mock
    private StrategyCommandRepository strategyCommandRepository;

    @Test
    @DisplayName("INACTIVE 전략을 활성화하면 ACTIVE 상태가 된다")
    void activateStrategy() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        Strategy strategy = createStrategy(userId);
        StrategyActivationCommandService service =
                new StrategyActivationCommandService(strategyCommandRepository);

        given(strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId))
                .willReturn(Optional.of(strategy));

        // when
        StrategyActivationResponse response =
                service.changeActivation(
                        userId,
                        strategyId,
                        true,
                        StrategyActivationSnapshot.from(strategy)
                );

        // then
        assertThat(response.status()).isEqualTo(StrategyStatus.ACTIVE);
        assertThat(response.activatedAt()).isNotNull();
        assertThat(strategy.getStatus()).isEqualTo(StrategyStatus.ACTIVE);
        assertThat(strategy.getActivatedAt()).isNotNull();
    }

    @Test
    @DisplayName("ACTIVE 전략을 비활성화하면 INACTIVE 상태가 되고 activatedAt이 초기화된다")
    void deactivateStrategy() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        Strategy strategy = createStrategy(userId);
        strategy.activate();
        StrategyActivationCommandService service =
                new StrategyActivationCommandService(strategyCommandRepository);

        given(strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId))
                .willReturn(Optional.of(strategy));

        // when
        StrategyActivationResponse response =
                service.changeActivation(
                        userId,
                        strategyId,
                        false,
                        StrategyActivationSnapshot.from(strategy)
                );

        // then
        assertThat(response.status()).isEqualTo(StrategyStatus.INACTIVE);
        assertThat(response.activatedAt()).isNull();
        assertThat(strategy.getStatus()).isEqualTo(StrategyStatus.INACTIVE);
        assertThat(strategy.getActivatedAt()).isNull();
    }

    @Test
    @DisplayName("전략이 없으면 상태를 변경할 수 없다")
    void changeActivationStrategyNotFound() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        StrategyActivationCommandService service =
                new StrategyActivationCommandService(strategyCommandRepository);

        given(strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.changeActivation(
                userId,
                strategyId,
                true,
                new StrategyActivationSnapshot(null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(StrategyErrorCode.STRATEGY_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("검증 이후 PER/PBR 조건이 변경되면 전략을 활성화할 수 없다")
    void changeActivationFailsWhenConditionsChangedAfterValidation() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        Strategy strategy = Strategy.create(
                userId,
                UUID.randomUUID(),
                "005930",
                "테스트 전략",
                70_000L,
                80_000L,
                new BigDecimal("5.0000"),
                null,
                3_000_000L,
                new BigDecimal("10.0000"),
                new BigDecimal("1.5000"),
                null
        );
        StrategyActivationSnapshot snapshot = StrategyActivationSnapshot.from(strategy);
        strategy.update(
                null,
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("20.0000"),
                null,
                null
        );
        StrategyActivationCommandService service =
                new StrategyActivationCommandService(strategyCommandRepository);

        given(strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId))
                .willReturn(Optional.of(strategy));

        // when & then
        assertThatThrownBy(() -> service.changeActivation(
                userId,
                strategyId,
                true,
                snapshot
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(StrategyErrorCode.INVALID_STRATEGY);
                });
    }

    private Strategy createStrategy(UUID userId) {
        return Strategy.create(
                userId,
                UUID.randomUUID(),
                "005930",
                "테스트 전략",
                70_000L,
                80_000L,
                new BigDecimal("5.0000"),
                null,
                3_000_000L,
                null,
                null,
                null
        );
    }
}
