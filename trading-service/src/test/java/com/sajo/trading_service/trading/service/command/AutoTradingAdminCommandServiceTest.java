package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.repository.command.AutoTradingCommandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoTradingAdminCommandServiceTest {

    @Mock
    private AutoTradingCommandRepository autoTradingCommandRepository;

    @InjectMocks
    private AutoTradingAdminCommandService autoTradingAdminCommandService;

    @Test
    @DisplayName("관리자는 AutoTrading을 긴급 중지할 수 있다")
    void suspend() {
        // given
        UUID autoTradingId = UUID.randomUUID();
        AutoTrading autoTrading = mock(AutoTrading.class);

        when(autoTradingCommandRepository.findByIdForUpdate(autoTradingId))
                .thenReturn(Optional.of(autoTrading));

        // when
        autoTradingAdminCommandService.suspend(autoTradingId);

        // then
        verify(autoTrading)
                .suspendByAdmin();
    }

    @Test
    @DisplayName("관리자는 AutoTrading 긴급 중지를 해제할 수 있다")
    void resume() {
        // given
        UUID autoTradingId = UUID.randomUUID();
        AutoTrading autoTrading = mock(AutoTrading.class);

        when(autoTradingCommandRepository.findByIdForUpdate(autoTradingId))
                .thenReturn(Optional.of(autoTrading));

        // when
        autoTradingAdminCommandService.resume(autoTradingId);

        // then
        verify(autoTrading)
                .resumeByAdmin();
    }

    @Test
    @DisplayName("존재하지 않는 AutoTrading을 긴급 중지하면 실패한다")
    void suspendNotFound() {
        // given
        UUID autoTradingId = UUID.randomUUID();

        when(autoTradingCommandRepository.findByIdForUpdate(autoTradingId))
                .thenReturn(Optional.empty());

        // when & then
        assertThrows(
                BusinessException.class,
                () -> autoTradingAdminCommandService.suspend(autoTradingId)
        );
    }

    @Test
    @DisplayName("존재하지 않는 AutoTrading의 긴급 중지를 해제하면 실패한다")
    void resumeNotFound() {
        // given
        UUID autoTradingId = UUID.randomUUID();

        when(autoTradingCommandRepository.findByIdForUpdate(autoTradingId))
                .thenReturn(Optional.empty());

        // when & then
        assertThrows(
                BusinessException.class,
                () -> autoTradingAdminCommandService.resume(autoTradingId)
        );
    }
}