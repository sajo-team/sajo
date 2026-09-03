package com.sajo.market_service.strategy.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.strategy.controller.dto.response.StrategyDetailResponse;
import com.sajo.market_service.strategy.controller.dto.response.StrategyListResponse;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.domain.StrategyStatus;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import com.sajo.market_service.strategy.repository.query.StrategyQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StrategyQueryServiceTest {

    @Mock
    private StrategyQueryRepository strategyQueryRepository;

    private StrategyQueryService strategyQueryService;

    @BeforeEach
    void setUp() {
        strategyQueryService = new StrategyQueryService(strategyQueryRepository);
    }

    @Test
    @DisplayName("전략 목록을 조회하면 페이지 정보와 함께 요약 정보를 반환한다")
    void getStrategies() {
        // given
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);

        Strategy strategy = Strategy.create(
                userId, UUID.randomUUID(), "005930", "삼성전자 눌림목 전략",
                70_000L, 80_000L, new BigDecimal("5.0000"), null,
                3_000_000L, null, null, null
        );
        Page<Strategy> page = new PageImpl<>(List.of(strategy), pageable, 1);

        given(strategyQueryRepository.findStrategies(userId, null, null, pageable))
                .willReturn(page);

        // when
        StrategyListResponse response =
                strategyQueryService.getStrategies(userId, null, null, pageable);

        // then
        assertThat(response.strategies()).hasSize(1);
        assertThat(response.strategies().get(0).strategyName()).isEqualTo("삼성전자 눌림목 전략");
        assertThat(response.strategies().get(0).stockCode()).isEqualTo("005930");
        assertThat(response.strategies().get(0).status()).isEqualTo(StrategyStatus.INACTIVE);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("전달받은 필터 조건과 페이징 정보를 그대로 리포지토리에 전달한다")
    void getStrategiesPassesFilterToRepository() {
        // given
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(1, 5);

        given(strategyQueryRepository.findStrategies(userId, StrategyStatus.ACTIVE, "005930", pageable))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        // when
        strategyQueryService.getStrategies(userId, StrategyStatus.ACTIVE, "005930", pageable);

        // then
        verify(strategyQueryRepository).findStrategies(userId, StrategyStatus.ACTIVE, "005930", pageable);
    }

    @Test
    @DisplayName("조회 결과가 없으면 빈 목록을 반환한다")
    void getStrategiesReturnsEmptyList() {
        // given
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);

        given(strategyQueryRepository.findStrategies(userId, null, null, pageable))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        // when
        StrategyListResponse response =
                strategyQueryService.getStrategies(userId, null, null, pageable);

        // then
        assertThat(response.strategies()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }

    @Test
    @DisplayName("전략 상세를 조회하면 사세 응답을 반환한다.")
    void getStrategy() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        Strategy strategy = Strategy.create(
                userId,
                UUID.randomUUID(),
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

        given(strategyQueryRepository
                .findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId))
                .willReturn(Optional.of(strategy));

        // when
        StrategyDetailResponse response = strategyQueryService.getStrategy(userId, strategyId);

        // then
        assertThat(response.stockCode()).isEqualTo("005930");
        assertThat(response.strategyName()).isEqualTo("삼성전자 눌림목 전략");
        assertThat(response.buyConditionPrice()).isEqualTo(70_000L);
        assertThat(response.sellConditionPrice()).isEqualTo(80_000L);
        assertThat(response.perCondition()).isEqualByComparingTo("15.0000");
        assertThat(response.pbrCondition()).isEqualByComparingTo("1.2000");
        assertThat(response.roeCondition()).isEqualByComparingTo("10.0000");
        assertThat(response.allocatedAmount()).isEqualTo(3_000_000L);
        assertThat(response.status()).isEqualTo(StrategyStatus.INACTIVE);
    }

    @Test
    @DisplayName("전략 상세 조회 결과가 없으면 예외가 발생한다.")
    void getStrategyThrowsExceptionWhenNotFound() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        given(strategyQueryRepository
                .findByIdAndUserIdAndDeletedAtIsNull(strategyId, userId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> strategyQueryService.getStrategy(userId,strategyId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(StrategyErrorCode.STRATEGY_NOT_FOUND.getMessage());
    }
}
