package com.sajo.market_service.strategy.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.strategy.controller.dto.request.StrategyActivationRequest;
import com.sajo.market_service.strategy.controller.dto.request.StrategyCreateRequest;
import com.sajo.market_service.strategy.controller.dto.request.StrategyUpdateRequest;
import com.sajo.market_service.strategy.controller.dto.response.StrategyActivationResponse;
import com.sajo.market_service.strategy.controller.dto.response.StrategyCreateResponse;
import com.sajo.market_service.strategy.controller.dto.response.StrategyUpdateResponse;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.domain.StrategyStatus;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import com.sajo.market_service.strategy.repository.command.StrategyCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StrategyCommandServiceTest {

    @Mock
    private StrategyCommandRepository strategyCommandRepository;

    private StrategyCommandService strategyCommandService;

    @BeforeEach
    void setUp() {
        strategyCommandService = new StrategyCommandService(strategyCommandRepository);
    }

    @Test
    @DisplayName("전략을 생성하면 기본 상태는 INACTIVE로 저장된다")
    void createStrategy() {
        // given
        UUID userId = UUID.randomUUID();
        UUID stockId = UUID.randomUUID();

        StrategyCreateRequest request = new StrategyCreateRequest(
                stockId,
                "005930",
                "삼성전자 눌림목 전략",
                70_000L,
                80_000L,
                new BigDecimal("5.0000"),
                new BigDecimal("10.0000"),
                3_000_000L,
                new BigDecimal("15.0000"),
                new BigDecimal("1.2000"),
                new BigDecimal("10.0000")
        );

        given(strategyCommandRepository.save(any(Strategy.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        StrategyCreateResponse response =
                strategyCommandService.createStrategy(userId, request);

        // then
        ArgumentCaptor<Strategy> captor = ArgumentCaptor.forClass(Strategy.class);
        verify(strategyCommandRepository).save(captor.capture());

        Strategy savedStrategy = captor.getValue();
        assertThat(savedStrategy.getUserId()).isEqualTo(userId);
        assertThat(savedStrategy.getStockId()).isEqualTo(stockId);
        assertThat(savedStrategy.getStockCode()).isEqualTo("005930");
        assertThat(savedStrategy.getStrategyName()).isEqualTo("삼성전자 눌림목 전략");
        assertThat(savedStrategy.getBuyConditionPrice()).isEqualTo(70_000L);
        assertThat(savedStrategy.getSellConditionPrice()).isEqualTo(80_000L);
        assertThat(savedStrategy.getStopLossRate()).isEqualByComparingTo("5.0000");
        assertThat(savedStrategy.getAllocatedAmount()).isEqualTo(3_000_000L);
        assertThat(savedStrategy.getStatus()).isEqualTo(StrategyStatus.INACTIVE); // 핵심 요구사항

        assertThat(response.strategyName()).isEqualTo("삼성전자 눌림목 전략");
        assertThat(response.status()).isEqualTo(StrategyStatus.INACTIVE);
    }

    @Test
    @DisplayName("선택 항목(목표수익률, PER·PBR·ROE 조건) 없이도 전략을 생성할 수 있다")
    // 모든 조건 구조가 일치하기에 대표 케이스 검증
    void createStrategyWithoutOptionalFields() {
        // given
        UUID userId = UUID.randomUUID();
        UUID stockId = UUID.randomUUID();

        StrategyCreateRequest request = new StrategyCreateRequest(
                stockId, "005930", "삼성전자 눌림목 전략",
                70_000L, 80_000L, new BigDecimal("5.0000"),
                null, // targetReturnRate 생략
                3_000_000L,
                null, null, null // per/pbr/roe 생략
        );

        given(strategyCommandRepository.save(any(Strategy.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        StrategyCreateResponse response =
                strategyCommandService.createStrategy(userId, request);

        // then
        assertThat(response.targetReturnRate()).isNull();
        assertThat(response.perCondition()).isNull();
        assertThat(response.pbrCondition()).isNull();
        assertThat(response.roeCondition()).isNull();
        assertThat(response.status()).isEqualTo(StrategyStatus.INACTIVE);
    }

    @Test
    @DisplayName("매수 조건 가격이 0 이하이면 전략을 생성할 수 없다")
    // 숫자 검증
    void createStrategyInvalidBuyConditionPrice() {
        // given
        UUID userId = UUID.randomUUID();
        StrategyCreateRequest request = new StrategyCreateRequest(
                UUID.randomUUID(), "005930", "테스트 전략",
                0L, 80_000L, new BigDecimal("5.0000"), null,
                3_000_000L, null, null, null
        );

        // when & then
        assertThatThrownBy(() -> strategyCommandService.createStrategy(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(StrategyErrorCode.INVALID_STRATEGY);
                });

        verify(strategyCommandRepository, never()).save(any(Strategy.class));
    }

    @Test
    @DisplayName("전략명이 공백이면 전략을 생성할 수 없다")
    // 문자열 검증
    void createStrategyBlankName() {
        // given
        UUID userId = UUID.randomUUID();
        StrategyCreateRequest request = new StrategyCreateRequest(
                UUID.randomUUID(), "005930", "   ",
                70_000L, 80_000L, new BigDecimal("5.0000"), null,
                3_000_000L, null, null, null
        );

        // when & then
        assertThatThrownBy(() -> strategyCommandService.createStrategy(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(StrategyErrorCode.INVALID_STRATEGY);
                });

        verify(strategyCommandRepository, never()).save(any(Strategy.class));
    }

    @Test
    @DisplayName("전략을 수정하면 변경된 전략 정보를 반환한다.")
    void updateStrategy() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        Strategy strategy = Strategy.create(
                userId,
                UUID.randomUUID(),
                "005930",
                "기존 전략",
                70_000L,
                80_000L,
                new BigDecimal("5.0000"),
                new BigDecimal("10.0000"),
                3_000_000L,
                null,
                null,
                null
        );

        StrategyUpdateRequest request = new StrategyUpdateRequest(
                "수정된 전략",
                71_000L,
                82_000L,
                new BigDecimal("4.0000"),
                new BigDecimal("12.0000"),
                4_000_000L,
                new BigDecimal("15.0000"),
                new BigDecimal("1.2000"),
                new BigDecimal("10.0000")
        );

        given(strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId))
                .willReturn(Optional.of(strategy));

        // when
        StrategyUpdateResponse response = strategyCommandService.updateStrategy(
                userId, strategyId, request
        );

        // then
        assertThat(response.strategyName()).isEqualTo("수정된 전략");
        assertThat(response.buyConditionPrice()).isEqualTo(71_000L);
        assertThat(response.sellConditionPrice()).isEqualTo(82_000L);
        assertThat(response.stopLossRate()).isEqualByComparingTo("4.0000");
        assertThat(response.targetReturnRate()).isEqualByComparingTo("12.0000");
        assertThat(response.allocatedAmount()).isEqualTo(4_000_000L);
        assertThat(response.perCondition()).isEqualByComparingTo("15.0000");
        assertThat(response.pbrCondition()).isEqualByComparingTo("1.2000");
        assertThat(response.roeCondition()).isEqualByComparingTo("10.0000");
    }

    @Test
    @DisplayName("수정할 전략이 없는 겨우 예외 발생")
    void updateStrategyNotFound() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        StrategyUpdateRequest request = new StrategyUpdateRequest(
                "수정된 전략",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        given(strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId))
            .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> strategyCommandService.updateStrategy(userId, strategyId, request))
        .isInstanceOf(BusinessException.class)
        .satisfies(exception -> {
            BusinessException businessException = (BusinessException) exception;
            assertThat(businessException.getErrorCode())
            .isEqualTo(StrategyErrorCode.STRATEGY_NOT_FOUND);
        });
    }

    @Test
    @DisplayName("전략 수정 시 전달된 필드만 반영")
    void updateStrategyPartially() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        Strategy strategy = Strategy.create(
                userId,
                UUID.randomUUID(),
                "005930",
                "기존 전략",
                70_000L,
                80_000L,
                new BigDecimal("5.0000"),
                null,
                3_000_000L,
                null,
                null,
                null
        );

        StrategyUpdateRequest request = new StrategyUpdateRequest(
                "수정된 전략",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        given(strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId))
                .willReturn(Optional.of(strategy));

        // when
        StrategyUpdateResponse response = strategyCommandService.updateStrategy(userId, strategyId, request);

        // then
        assertThat(response.strategyName()).isEqualTo("수정된 전략");
        assertThat(response.buyConditionPrice()).isEqualTo(70_000L);
        assertThat(response.stopLossRate()).isEqualByComparingTo("5.0000");
    }

    @Test
    @DisplayName("전략을 삭제하면 soft delete 처리된다.")
    void deleteStrategy() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        Strategy strategy = Strategy.create(
                userId,
                UUID.randomUUID(),
                "005930",
                "삭제할 전략",
                70_000L,
                80_000L,
                new BigDecimal("5.0000"),
                null,
                3_000_000L,
                null,
                null,
                null
        );

        given(strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId))
                .willReturn(Optional.of(strategy));

        // when
        strategyCommandService.deleteStrategy(userId, strategyId);

        // then
        assertThat(strategy.getStatus()).isEqualTo(StrategyStatus.DELETED);
        assertThat(strategy.isDeleted()).isTrue();
        assertThat(strategy.getDeletedBy()).isEqualTo(userId);
        assertThat(strategy.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("삭제할 전략이 없으면 예외가 발생한다")
    void deleteStrategyNotFound() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        given(strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> strategyCommandService.deleteStrategy(userId, strategyId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(StrategyErrorCode.STRATEGY_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("INACTIVE 전략을 활성화하면 ACTIVE 상태가 되고 activatedAt이 설정된다.")
    void activateStrategy() {
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
                null,
                null,
                null
        );

        StrategyActivationRequest request = new StrategyActivationRequest(true);

        given(strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId))
                .willReturn(Optional.of(strategy));

        // when
        StrategyActivationResponse response = strategyCommandService.updateActivation(userId, strategyId, request);

        // then
        assertThat(response.status()).isEqualTo(StrategyStatus.ACTIVE);
        assertThat(response.activatedAt()).isNotNull();
        assertThat(strategy.getStatus()).isEqualTo(StrategyStatus.ACTIVE);
        assertThat(strategy.getActivatedAt()).isNotNull();
    }

    @Test
    @DisplayName("ACTIVE 전략을 비활성화하면 INACTIVE 상태가 되고, activatedAt이 초기화된다.")
    void deactivateStrategy() {
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
                null,
                null,
                null
        );
        strategy.activate();

        StrategyActivationRequest request = new StrategyActivationRequest(false);
        given(strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId))
                .willReturn(Optional.of(strategy));

        // when
        StrategyActivationResponse response = strategyCommandService.updateActivation(userId, strategyId, request);

        // then
        assertThat(response.status()).isEqualTo(StrategyStatus.INACTIVE);
        assertThat(response.activatedAt()).isNull();
        assertThat(strategy.getStatus()).isEqualTo(StrategyStatus.INACTIVE);
        assertThat(strategy.getActivatedAt()).isNull();
    }

    @Test
    @DisplayName("ACTIVE 상태의 전략은 수정할 수 없다.")
    void updateActivateStrategyFails() {
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
                null,
                null,
                null
        );
        strategy.activate();

        StrategyUpdateRequest request = new StrategyUpdateRequest(
                "수정 전략",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        given(strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId))
                .willReturn(Optional.of(strategy));

        // when & then
        assertThatThrownBy(() -> strategyCommandService.updateStrategy(userId, strategyId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(StrategyErrorCode.INVALID_STRATEGY);
                });
    }

    @Test
    @DisplayName("ACTIVE 상태의 전략은 삭제할 수 없다.")
    void deleteActivateStrategyFails() {
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
                null,
                null,
                null
        );
        strategy.activate();

        given(strategyCommandRepository.findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId))
                .willReturn(Optional.of(strategy));

        // when & then
        assertThatThrownBy(() -> strategyCommandService.deleteStrategy(userId, strategyId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(StrategyErrorCode.INVALID_STRATEGY);
                });
    }
}
