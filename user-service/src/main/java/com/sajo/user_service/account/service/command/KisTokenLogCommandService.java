package com.sajo.user_service.account.service.command;

import com.sajo.user_service.account.domain.EventType;
import com.sajo.user_service.account.domain.KisTokenLog;
import com.sajo.user_service.account.domain.KisTokenType;
import com.sajo.user_service.account.repository.command.KisTokenLogCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KisTokenLogCommandService {
    private final KisTokenLogCommandRepository kisTokenLogCommandRepository;

    @Transactional
    public void recordSuccess(UUID accountId, UUID userId, KisTokenType tokenType) {

        KisTokenLog tokenLog = KisTokenLog.createTokenLog(
                accountId, userId, EventType.TOKEN_ISSUE_SUCCESS, tokenType, null, null
        );

        kisTokenLogCommandRepository.save(tokenLog);
    }

    @Transactional
    public void recordFail(UUID accountId, UUID userId, KisTokenType tokenType, String errorCode, String errorMessage) {
        KisTokenLog tokenLog = KisTokenLog.createTokenLog(
                accountId, userId, EventType.TOKEN_ISSUE_FAILED, tokenType, errorCode, errorMessage
        );

        kisTokenLogCommandRepository.save(tokenLog);
    }

    // 폐기는 access token 전용 - KIS에 접속키(웹소켓) 폐기 API가 없음
    @Transactional
    public void recordRevokeSuccess(UUID accountId, UUID userId) {

        KisTokenLog tokenLog = KisTokenLog.createTokenLog(
                accountId, userId, EventType.TOKEN_REVOKE_SUCCESS, KisTokenType.ACCESS_TOKEN, null, null
        );

        kisTokenLogCommandRepository.save(tokenLog);
    }

    @Transactional
    public void recordRevokeFail(UUID accountId, UUID userId, String errorCode, String errorMessage) {
        KisTokenLog tokenLog = KisTokenLog.createTokenLog(
                accountId, userId, EventType.TOKEN_REVOKE_FAILED, KisTokenType.ACCESS_TOKEN, errorCode, errorMessage
        );

        kisTokenLogCommandRepository.save(tokenLog);
    }
}
