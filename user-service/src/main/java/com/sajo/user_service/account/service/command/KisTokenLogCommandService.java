package com.sajo.user_service.account.service.command;

import com.sajo.user_service.account.domain.EventType;
import com.sajo.user_service.account.domain.KisTokenLog;
import com.sajo.user_service.account.domain.KisTokenType;
import com.sajo.user_service.account.repository.command.KisTokenLogCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisTokenLogCommandService {
    private final KisTokenLogCommandRepository kisTokenLogCommandRepository;

    @Transactional
    public void recordSuccess(UUID accountId, UUID userId, KisTokenType tokenType) {
        save(KisTokenLog.createTokenLog(accountId, userId, EventType.TOKEN_ISSUE_SUCCESS, tokenType, null, null));
    }

    @Transactional
    public void recordFail(UUID accountId, UUID userId, KisTokenType tokenType, String errorCode, String errorMessage) {
        save(KisTokenLog.createTokenLog(
                accountId, userId, EventType.TOKEN_ISSUE_FAILED, tokenType, errorCode, errorMessage));
    }

    // 폐기는 access token 전용 - KIS에 접속키(웹소켓) 폐기 API가 없음
    @Transactional
    public void recordRevokeSuccess(UUID accountId, UUID userId) {
        save(KisTokenLog.createTokenLog(
                accountId, userId, EventType.TOKEN_REVOKE_SUCCESS, KisTokenType.ACCESS_TOKEN, null, null));
    }

    @Transactional
    public void recordRevokeFail(UUID accountId, UUID userId, String errorCode, String errorMessage) {
        save(KisTokenLog.createTokenLog(
                accountId, userId, EventType.TOKEN_REVOKE_FAILED, KisTokenType.ACCESS_TOKEN, errorCode, errorMessage));
    }

    // 이력 기록 실패(DB 오류 등)가 호출자의 핵심 흐름(토큰 발급/폐기)에 영향을 주면 안 된다.
    // saveAndFlush를 써야 이 메서드(트랜잭션 경계) 안에서 실제 INSERT 실패가 드러나 여기서 잡힌다.
    // PostgreSQL은 statement 오류 시 트랜잭션 전체를 abort 상태로 만들기 때문에, 여기서 예외를
    // 삼키기만 하면 이후 커밋 시점에 TransactionSystemException이 호출자에게 그대로 전파될 수 있다 -
    // setRollbackOnly로 커밋 대신 롤백하도록 명시해 이 메서드가 항상 조용히 반환되게 한다.
    private void save(KisTokenLog tokenLog) {
        try {
            kisTokenLogCommandRepository.saveAndFlush(tokenLog);
        } catch (Exception e) {
            log.warn("KIS 토큰 이력 저장 실패. eventType={}, tokenType={}",
                    tokenLog.getEventType(), tokenLog.getTokenType(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }
}
