package com.sajo.user_service.account.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.crypto.HmacSha256Hasher;
import com.sajo.user_service.account.domain.Account;
import com.sajo.user_service.account.exception.AccountErrorCode;
import com.sajo.user_service.account.repository.query.AccountQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountQueryService {

    private final AccountQueryRepository accountQueryRepository;
    private final HmacSha256Hasher hmacSha256Hasher;

    // 외부 API(KIS) 호출 전에 먼저 걸러내기 위한 사전 체크 - 저장 시점 재확인이 최종 안전장치
    @Transactional(readOnly = true)
    public void validateCreatable(UUID userId, String accountNo) {
        if (accountQueryRepository.existsByUserIdAndDeletedAtIsNull(userId)) {
            throw new BusinessException(AccountErrorCode.ALREADY_HAS_ACCOUNT);
        }

        String accountNoHash = hmacSha256Hasher.hash(accountNo);
        if (accountQueryRepository.existsByAccountNoHashAndDeletedAtIsNull(accountNoHash)) {
            throw new BusinessException(AccountErrorCode.DUPLICATE_ACCOUNT_NO);
        }
    }

    @Transactional(readOnly = true)
    public Account getAccountByUserId(UUID userId) {
        return accountQueryRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));
    }
}
