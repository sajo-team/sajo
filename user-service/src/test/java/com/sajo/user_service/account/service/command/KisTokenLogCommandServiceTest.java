package com.sajo.user_service.account.service.command;

import com.sajo.user_service.account.domain.EventType;
import com.sajo.user_service.account.domain.KisTokenLog;
import com.sajo.user_service.account.domain.KisTokenType;
import com.sajo.user_service.account.repository.command.KisTokenLogCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KisTokenLogCommandServiceTest {

    @Mock
    private KisTokenLogCommandRepository kisTokenLogCommandRepository;

    private KisTokenLogCommandService kisTokenLogCommandService;

    @BeforeEach
    void setUp() {
        kisTokenLogCommandService = new KisTokenLogCommandService(kisTokenLogCommandRepository);
    }

    @Test
    @DisplayName("recordSuccess는 TOKEN_ISSUE_SUCCESS 이벤트를 tokenType과 함께 저장한다")
    void recordSuccessSavesIssueSuccessEvent() {
        // given
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // when
        kisTokenLogCommandService.recordSuccess(accountId, userId, KisTokenType.APPROVAL_KEY);

        // then
        ArgumentCaptor<KisTokenLog> captor = ArgumentCaptor.forClass(KisTokenLog.class);
        verify(kisTokenLogCommandRepository).saveAndFlush(captor.capture());
        KisTokenLog saved = captor.getValue();
        assertThat(saved.getAccountId()).isEqualTo(accountId);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getEventType()).isEqualTo(EventType.TOKEN_ISSUE_SUCCESS);
        assertThat(saved.getTokenType()).isEqualTo(KisTokenType.APPROVAL_KEY);
        assertThat(saved.getErrorCode()).isNull();
        assertThat(saved.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("recordFail은 TOKEN_ISSUE_FAILED 이벤트를 에러코드/메시지와 함께 저장한다")
    void recordFailSavesIssueFailedEvent() {
        // given
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // when
        kisTokenLogCommandService.recordFail(
                accountId, userId, KisTokenType.ACCESS_TOKEN, "EGW00133", "1분당 1회 제한 초과");

        // then
        ArgumentCaptor<KisTokenLog> captor = ArgumentCaptor.forClass(KisTokenLog.class);
        verify(kisTokenLogCommandRepository).saveAndFlush(captor.capture());
        KisTokenLog saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo(EventType.TOKEN_ISSUE_FAILED);
        assertThat(saved.getTokenType()).isEqualTo(KisTokenType.ACCESS_TOKEN);
        assertThat(saved.getErrorCode()).isEqualTo("EGW00133");
        assertThat(saved.getErrorMessage()).isEqualTo("1분당 1회 제한 초과");
    }

    @Test
    @DisplayName("recordRevokeSuccess/recordRevokeFail은 항상 ACCESS_TOKEN으로 기록한다 (접속키 폐기 API 없음)")
    void recordRevokeAlwaysUsesAccessTokenType() {
        // given
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // when
        kisTokenLogCommandService.recordRevokeSuccess(accountId, userId);
        kisTokenLogCommandService.recordRevokeFail(accountId, userId, "EGW00201", "초당 거래건수 초과");

        // then
        ArgumentCaptor<KisTokenLog> captor = ArgumentCaptor.forClass(KisTokenLog.class);
        verify(kisTokenLogCommandRepository, times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(KisTokenLog::getTokenType)
                .containsExactly(KisTokenType.ACCESS_TOKEN, KisTokenType.ACCESS_TOKEN);
        assertThat(captor.getAllValues())
                .extracting(KisTokenLog::getEventType)
                .containsExactly(EventType.TOKEN_REVOKE_SUCCESS, EventType.TOKEN_REVOKE_FAILED);
    }

    @Test
    @DisplayName("저장 자체가 실패해도(DB 오류 등) 예외를 던지지 않는다 - 이력 기록 실패가 호출자의 핵심 흐름을 막으면 안 됨")
    void recordSuccessDoesNotThrowWhenSaveFails() {
        // given
        willThrow(new RuntimeException("DB 저장 실패"))
                .given(kisTokenLogCommandRepository).saveAndFlush(any());

        // when & then
        assertThatCode(() ->
                kisTokenLogCommandService.recordSuccess(UUID.randomUUID(), UUID.randomUUID(), KisTokenType.ACCESS_TOKEN))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("recordFail도 저장 실패 시 예외를 던지지 않는다")
    void recordFailDoesNotThrowWhenSaveFails() {
        // given
        willThrow(new RuntimeException("DB 저장 실패"))
                .given(kisTokenLogCommandRepository).saveAndFlush(any());

        // when & then
        assertThatCode(() -> kisTokenLogCommandService.recordFail(
                UUID.randomUUID(), UUID.randomUUID(), KisTokenType.ACCESS_TOKEN, "EGW00133", "메시지"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("recordRevokeSuccess/recordRevokeFail도 저장 실패 시 예외를 던지지 않는다")
    void recordRevokeDoesNotThrowWhenSaveFails() {
        // given
        willThrow(new RuntimeException("DB 저장 실패"))
                .given(kisTokenLogCommandRepository).saveAndFlush(any());

        // when & then
        assertThatCode(() -> kisTokenLogCommandService.recordRevokeSuccess(UUID.randomUUID(), UUID.randomUUID()))
                .doesNotThrowAnyException();
        assertThatCode(() -> kisTokenLogCommandService.recordRevokeFail(
                UUID.randomUUID(), UUID.randomUUID(), "EGW00133", "메시지"))
                .doesNotThrowAnyException();
    }
}
