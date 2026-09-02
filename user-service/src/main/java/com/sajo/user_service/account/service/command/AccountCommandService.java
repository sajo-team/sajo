package com.sajo.user_service.account.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.crypto.HmacSha256Hasher;
import com.sajo.user_service.account.domain.Account;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.exception.AccountErrorCode;
import com.sajo.user_service.account.repository.command.AccountCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
class AccountCommandService {

    private final AccountCommandRepository accountCommandRepository;
    private final HmacSha256Hasher hmacSha256Hasher;

    @Transactional
    Account createAccount(
            UUID userId, String appKey, String secretKey, String accountNo, AccountType accountType) {

        // 유저당 계좌 1개 (1:1) 검증 - KIS 호출 사이의 레이스 윈도우를 잡기 위한 최종 재확인
        if (accountCommandRepository.existsByUserIdAndDeletedAtIsNull(userId)) {
            throw new BusinessException(AccountErrorCode.ALREADY_HAS_ACCOUNT);
        }

        // 계좌 중복 검증
        String accountNoHash = hmacSha256Hasher.hash(accountNo);
        if (accountCommandRepository.existsByAccountNoHashAndDeletedAtIsNull(accountNoHash)) {
            throw new BusinessException(AccountErrorCode.DUPLICATE_ACCOUNT_NO);
        }

        Account account = Account.createAccount(userId, appKey, secretKey, accountNo, accountNoHash, accountType);

        try {
            return accountCommandRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(AccountErrorCode.DUPLICATE_ACCOUNT_REQUEST);
        }
    }
}
