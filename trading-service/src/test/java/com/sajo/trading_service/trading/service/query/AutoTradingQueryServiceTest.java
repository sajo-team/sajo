package com.sajo.trading_service.trading.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.controller.dto.request.AutoTradingAdminSearchCondition;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingAdminResponse;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingGlobalSuspensionResponse;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingQueryResponse;
import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.domain.AutoTradingOperationControl;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.AutoTradingDirection;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.query.AutoTradingOperationControlQueryRepository;
import com.sajo.trading_service.trading.repository.query.AutoTradingQueryRepository;
import com.sajo.trading_service.trading.repository.query.OrderQueryRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoTradingQueryServiceTest {

    @Mock
    private AutoTradingQueryRepository autoTradingQueryRepository;

    @Mock
    private OrderQueryRepository orderQueryRepository;

    @Mock
    private AutoTradingOperationControlQueryRepository
            autoTradingOperationControlQueryRepository;

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

    @Test
    @DisplayName("자동매매 단건 조회 시 최근 주문 정보를 함께 반환한다")
    void findByIdWithLatestOrder() {
        // given
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID latestOrderId = UUID.randomUUID();

        AutoTrading autoTrading =
                mock(AutoTrading.class);

        Order latestOrder =
                mock(Order.class);

        Instant autoTradingCreatedAt = Instant.now().minusSeconds(3600);
        Instant autoTradingUpdatedAt = Instant.now().minusSeconds(1800);
        Instant latestOrderCreatedAt = Instant.now();

        given(autoTradingQueryRepository
                .findByIdAndUserIdAndDeletedAtIsNull(
                        autoTradingId,
                        userId
                ))
                .willReturn(Optional.of(autoTrading));

        given(orderQueryRepository
                .findFirstByAutoTradingIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        autoTradingId
                ))
                .willReturn(Optional.of(latestOrder));

        given(autoTrading.getId())
                .willReturn(autoTradingId);

        given(autoTrading.getStrategyId())
                .willReturn(strategyId);

        given(autoTrading.getDirection())
                .willReturn(AutoTradingDirection.BOTH);

        given(autoTrading.getEnabled())
                .willReturn(true);

        given(autoTrading.getCreatedAt())
                .willReturn(autoTradingCreatedAt);

        given(autoTrading.getUpdatedAt())
                .willReturn(autoTradingUpdatedAt);

        given(latestOrder.getId())
                .willReturn(latestOrderId);

        given(latestOrder.getStatus())
                .willReturn(OrderStatus.FAILED);

        given(latestOrder.getCreatedAt())
                .willReturn(latestOrderCreatedAt);

        given(latestOrder.getFailureCode())
                .willReturn("KIS_ORDER_REJECTED");

        given(latestOrder.getFailureMessage())
                .willReturn("주문이 거절되었습니다.");

        // when
        AutoTradingQueryResponse response =
                autoTradingQueryService.findById(
                        autoTradingId,
                        userId
                );

        // then
        assertThat(response.autoTradingId())
                .isEqualTo(autoTradingId);

        assertThat(response.strategyId())
                .isEqualTo(strategyId);

        assertThat(response.direction())
                .isEqualTo(AutoTradingDirection.BOTH);

        assertThat(response.enabled())
                .isTrue();

        assertThat(response.latestOrderId())
                .isEqualTo(latestOrderId);

        assertThat(response.latestOrderStatus())
                .isEqualTo(OrderStatus.FAILED);

        assertThat(response.latestOrderCreatedAt())
                .isEqualTo(latestOrderCreatedAt);

        assertThat(response.latestFailureCode())
                .isEqualTo("KIS_ORDER_REJECTED");

        assertThat(response.latestFailureMessage())
                .isEqualTo("주문이 거절되었습니다.");

        verify(orderQueryRepository)
                .findFirstByAutoTradingIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        autoTradingId
                );
    }

    @Test
    @DisplayName("최근 주문이 없으면 최근 주문 정보는 null로 반환한다")
    void findByIdWithoutLatestOrder() {
        // given
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        AutoTrading autoTrading =
                mock(AutoTrading.class);

        given(autoTradingQueryRepository
                .findByIdAndUserIdAndDeletedAtIsNull(
                        autoTradingId,
                        userId
                ))
                .willReturn(Optional.of(autoTrading));

        given(orderQueryRepository
                .findFirstByAutoTradingIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        autoTradingId
                ))
                .willReturn(Optional.empty());

        given(autoTrading.getId())
                .willReturn(autoTradingId);

        given(autoTrading.getStrategyId())
                .willReturn(strategyId);

        given(autoTrading.getDirection())
                .willReturn(AutoTradingDirection.BUY_ONLY);

        given(autoTrading.getEnabled())
                .willReturn(false);

        // when
        AutoTradingQueryResponse response =
                autoTradingQueryService.findById(
                        autoTradingId,
                        userId
                );

        // then
        assertThat(response.autoTradingId())
                .isEqualTo(autoTradingId);

        assertThat(response.latestOrderId())
                .isNull();

        assertThat(response.latestOrderStatus())
                .isNull();

        assertThat(response.latestOrderCreatedAt())
                .isNull();

        assertThat(response.latestFailureCode())
                .isNull();

        assertThat(response.latestFailureMessage())
                .isNull();
    }

    @Test
    @DisplayName("자동매매 목록 조회 시 각 자동매매의 최근 주문 정보를 함께 반환한다")
    void findAllByUserIdWithLatestOrders() {
        // given
        UUID userId = UUID.randomUUID();

        UUID firstAutoTradingId = UUID.randomUUID();
        UUID secondAutoTradingId = UUID.randomUUID();

        UUID firstStrategyId = UUID.randomUUID();
        UUID secondStrategyId = UUID.randomUUID();

        UUID latestOrderId = UUID.randomUUID();

        AutoTrading firstAutoTrading =
                mock(AutoTrading.class);

        AutoTrading secondAutoTrading =
                mock(AutoTrading.class);

        Order latestOrder =
                mock(Order.class);

        PageRequest pageable =
                PageRequest.of(0, 10);

        Page<AutoTrading> autoTradingPage =
                new PageImpl<>(
                        List.of(
                                firstAutoTrading,
                                secondAutoTrading
                        ),
                        pageable,
                        2
                );

        given(autoTradingQueryRepository
                .findAllByUserIdAndDeletedAtIsNull(
                        userId,
                        pageable
                ))
                .willReturn(autoTradingPage);

        given(firstAutoTrading.getId())
                .willReturn(firstAutoTradingId);

        given(firstAutoTrading.getStrategyId())
                .willReturn(firstStrategyId);

        given(firstAutoTrading.getDirection())
                .willReturn(AutoTradingDirection.BUY_ONLY);

        given(firstAutoTrading.getEnabled())
                .willReturn(true);

        given(secondAutoTrading.getId())
                .willReturn(secondAutoTradingId);

        given(secondAutoTrading.getStrategyId())
                .willReturn(secondStrategyId);

        given(secondAutoTrading.getDirection())
                .willReturn(AutoTradingDirection.SELL_ONLY);

        given(secondAutoTrading.getEnabled())
                .willReturn(false);

        Instant latestOrderCreatedAt = Instant.now();

        given(latestOrder.getId())
                .willReturn(latestOrderId);

        given(latestOrder.getAutoTradingId())
                .willReturn(firstAutoTradingId);

        given(latestOrder.getStatus())
                .willReturn(OrderStatus.FAILED);

        given(latestOrder.getCreatedAt())
                .willReturn(latestOrderCreatedAt);

        given(latestOrder.getFailureCode())
                .willReturn("KIS_ORDER_REJECTED");

        given(latestOrder.getFailureMessage())
                .willReturn("주문이 거절되었습니다.");

        given(orderQueryRepository
                .findLatestOrdersByAutoTradingIds(anyList()))
                .willReturn(List.of(latestOrder));

        // when
        Page<AutoTradingQueryResponse> response =
                autoTradingQueryService.findAllByUserId(
                        userId,
                        pageable
                );

        // then
        assertThat(response.getTotalElements())
                .isEqualTo(2);

        AutoTradingQueryResponse first =
                response.getContent().get(0);

        assertThat(first.autoTradingId())
                .isEqualTo(firstAutoTradingId);

        assertThat(first.direction())
                .isEqualTo(AutoTradingDirection.BUY_ONLY);

        assertThat(first.latestOrderId())
                .isEqualTo(latestOrderId);

        assertThat(first.latestOrderStatus())
                .isEqualTo(OrderStatus.FAILED);

        assertThat(first.latestOrderCreatedAt())
                .isEqualTo(latestOrderCreatedAt);

        assertThat(first.latestFailureCode())
                .isEqualTo("KIS_ORDER_REJECTED");

        assertThat(first.latestFailureMessage())
                .isEqualTo("주문이 거절되었습니다.");

        AutoTradingQueryResponse second =
                response.getContent().get(1);

        assertThat(second.autoTradingId())
                .isEqualTo(secondAutoTradingId);

        assertThat(second.direction())
                .isEqualTo(AutoTradingDirection.SELL_ONLY);

        assertThat(second.latestOrderId())
                .isNull();

        assertThat(second.latestOrderStatus())
                .isNull();

        assertThat(second.latestFailureCode())
                .isNull();

        assertThat(second.latestFailureMessage())
                .isNull();

        verify(orderQueryRepository)
                .findLatestOrdersByAutoTradingIds(
                        anyList()
                );
    }

    @Test
    @DisplayName("자동매매 목록이 비어 있으면 최근 주문을 조회하지 않는다")
    void findAllByUserIdEmpty() {
        // given
        UUID userId = UUID.randomUUID();

        PageRequest pageable =
                PageRequest.of(0, 10);

        given(autoTradingQueryRepository
                .findAllByUserIdAndDeletedAtIsNull(
                        userId,
                        pageable
                ))
                .willReturn(Page.empty(pageable));

        // when
        Page<AutoTradingQueryResponse> response =
                autoTradingQueryService.findAllByUserId(
                        userId,
                        pageable
                );

        // then
        assertThat(response)
                .isEmpty();

        verify(orderQueryRepository, never())
                .findLatestOrdersByAutoTradingIds(anyList());
    }

    @Test
    @DisplayName("관리자는 전체 AutoTrading 긴급 중지 상태를 조회할 수 있다")
    void getGlobalSuspension_success() {
        // given
        AutoTradingOperationControl control =
                mock(AutoTradingOperationControl.class);

        given(autoTradingOperationControlQueryRepository
                .findByIdAndDeletedAtIsNull(
                        AutoTradingOperationControl.GLOBAL_CONTROL_ID
                ))
                .willReturn(Optional.of(control));

        given(control.isSuspended())
                .willReturn(true);

        // when
        AutoTradingGlobalSuspensionResponse response =
                autoTradingQueryService.getGlobalSuspension();

        // then
        assertThat(response.suspended())
                .isTrue();

        verify(autoTradingOperationControlQueryRepository)
                .findByIdAndDeletedAtIsNull(
                        AutoTradingOperationControl.GLOBAL_CONTROL_ID
                );
    }

    @Test
    @DisplayName("전체 AutoTrading 운영 제어 정보가 없으면 조회에 실패한다")
    void getGlobalSuspension_notFound() {
        // given
        given(autoTradingOperationControlQueryRepository
                .findByIdAndDeletedAtIsNull(
                        AutoTradingOperationControl.GLOBAL_CONTROL_ID
                ))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                autoTradingQueryService.getGlobalSuspension()
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    TradingErrorCode.AUTO_TRADING_OPERATION_CONTROL_NOT_FOUND
                            );
                });
    }

    @Test
    @DisplayName("관리자 AutoTrading 조회 시 관리자 중지 상태를 함께 반환한다")
    void findAllAutoTradingForAdmin_withAdminSuspended() {
        // given
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        AutoTrading autoTrading =
                mock(AutoTrading.class);

        Pageable pageable =
                PageRequest.of(0, 10);

        AutoTradingAdminSearchCondition condition =
                new AutoTradingAdminSearchCondition(
                        userId,
                        strategyId,
                        null,
                        null
                );

        Page<AutoTrading> page =
                new PageImpl<>(
                        List.of(autoTrading),
                        pageable,
                        1
                );

        given(autoTradingQueryRepository.findAllForAdmin(
                userId,
                strategyId,
                null,
                null,
                pageable
        )).willReturn(page);

        given(autoTrading.getId())
                .willReturn(autoTradingId);

        given(autoTrading.getUserId())
                .willReturn(userId);

        given(autoTrading.getStrategyId())
                .willReturn(strategyId);

        given(autoTrading.getDirection())
                .willReturn(AutoTradingDirection.BOTH);

        given(autoTrading.getEnabled())
                .willReturn(true);

        given(autoTrading.getAdminSuspended())
                .willReturn(true);

        given(orderQueryRepository
                .findLatestOrdersByAutoTradingIds(anyList()))
                .willReturn(List.of());

        // when
        Page<AutoTradingAdminResponse> response =
                autoTradingQueryService.findAllAutoTradingForAdmin(
                        condition,
                        pageable
                );

        // then
        AutoTradingAdminResponse result =
                response.getContent().get(0);

        assertThat(result.autoTradingId())
                .isEqualTo(autoTradingId);

        assertThat(result.enabled())
                .isTrue();

        assertThat(result.adminSuspended())
                .isTrue();
    }
}