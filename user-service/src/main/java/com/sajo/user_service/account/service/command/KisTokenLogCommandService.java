package com.sajo.user_service.account.service.command;

import com.sajo.user_service.account.domain.EventType;
import com.sajo.user_service.account.domain.KisTokenLog;
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
    public void recordSuccess(UUID accountId, UUID userId) {

        KisTokenLog tokenLog = KisTokenLog.createTokenLog(
                accountId, userId, EventType.TOKEN_ISSUE_SUCCESS, null, null
        );

        kisTokenLogCommandRepository.save(tokenLog);
    }

    @Transactional
    public void recordFail(UUID accountId, UUID userId, String errorCode, String errorMessage) {
        KisTokenLog tokenLog = KisTokenLog.createTokenLog(
                accountId, userId, EventType.TOKEN_ISSUE_FAILED, errorCode, errorMessage
        );

        kisTokenLogCommandRepository.save(tokenLog);
    }
}
