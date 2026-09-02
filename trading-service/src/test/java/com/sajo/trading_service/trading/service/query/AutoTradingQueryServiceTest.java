package com.sajo.trading_service.trading.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingQueryResponse;
import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.query.AutoTradingQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoTradingQueryServiceTest {

    @Mock
    private AutoTradingQueryRepository autoTradingQueryRepository;

    @InjectMocks
    private AutoTradingQueryService autoTradingQueryService;

    private UUID userId;
    private UUID autoTradingId;
    private UUID strategyId;
    private AutoTrading autoTrading;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        autoTradingId = UUID.randomUUID();
        strategyId = UUID.randomUUID();

        autoTrading = org.mockito.Mockito.mock(AutoTrading.class);
    }

    @Test
    @DisplayName("자동매매 설정 목록을 조회한다")
    void findAllByUserId_success() {
        // given
        Pageable pageable = PageRequest.of(0, 10);

        when(autoTrading.getId()).thenReturn(autoTradingId);
        when(autoTrading.getStrategyId()).thenReturn(strategyId);
        when(autoTrading.getEnabled()).thenReturn(true);

        Page<AutoTrading> page =
                new PageImpl<>(
                        List.of(autoTrading),
                        pageable,
                        1
                );

        when(autoTradingQueryRepository
                .findAllByUserIdAndDeletedAtIsNull(userId, pageable))
                .thenReturn(page);

        // when
        Page<AutoTradingQueryResponse> result =
                autoTradingQueryService.findAllByUserId(
                        userId,
                        pageable
                );

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).autoTradingId())
                .isEqualTo(autoTradingId);
        assertThat(result.getContent().get(0).strategyId())
                .isEqualTo(strategyId);
        assertThat(result.getContent().get(0).enabled())
                .isTrue();
    }

    @Test
    @DisplayName("자동매매 설정이 없으면 빈 페이지를 반환한다")
    void findAllByUserId_empty() {
        // given
        Pageable pageable = PageRequest.of(0, 10);

        when(autoTradingQueryRepository
                .findAllByUserIdAndDeletedAtIsNull(userId, pageable))
                .thenReturn(Page.empty(pageable));

        // when
        Page<AutoTradingQueryResponse> result =
                autoTradingQueryService.findAllByUserId(
                        userId,
                        pageable
                );

        // then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("자동매매 설정 단건을 조회한다")
    void findById_success() {
        // given
        when(autoTrading.getId()).thenReturn(autoTradingId);
        when(autoTrading.getStrategyId()).thenReturn(strategyId);
        when(autoTrading.getEnabled()).thenReturn(true);

        when(autoTradingQueryRepository
                .findByIdAndUserIdAndDeletedAtIsNull(
                        autoTradingId,
                        userId
                ))
                .thenReturn(Optional.of(autoTrading));

        // when
        AutoTradingQueryResponse result =
                autoTradingQueryService.findById(
                        autoTradingId,
                        userId
                );

        // then
        assertThat(result.autoTradingId())
                .isEqualTo(autoTradingId);
        assertThat(result.strategyId())
                .isEqualTo(strategyId);
        assertThat(result.enabled())
                .isTrue();
    }

    @Test
    @DisplayName("자동매매 설정을 찾을 수 없으면 예외가 발생한다")
    void findById_notFound() {
        // given
        when(autoTradingQueryRepository
                .findByIdAndUserIdAndDeletedAtIsNull(
                        autoTradingId,
                        userId
                ))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                autoTradingQueryService.findById(
                        autoTradingId,
                        userId
                ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    TradingErrorCode.AUTO_TRADING_NOT_FOUND
                            );
                });
    }
}