package com.sajo.user_service.account.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.controller.dto.request.AccountCreateRequest;
import com.sajo.user_service.account.crypto.HmacSha256Hasher;
import com.sajo.user_service.account.domain.Account;
import com.sajo.user_service.account.exception.AccountErrorCode;
import com.sajo.user_service.account.repository.command.AccountCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountCommandService {

    private final AccountCommandRepository accountCommandRepository;
    private final HmacSha256Hasher hmacSha256Hasher;

    @Transactional
    public Account createAccount(UUID userId, AccountCreateRequest request) {

        // 유저당 계좌 1개 (1:1) 검증
        if (accountCommandRepository.existsByUserId(userId)) {
            throw new BusinessException(AccountErrorCode.ALREADY_HAS_ACCOUNT);
        }

        // 계좌 중복 검증
        String accountNoHash = hmacSha256Hasher.hash(request.accountNo());
        if (accountCommandRepository.existsByAccountNoHash(accountNoHash)) {
            throw new BusinessException(AccountErrorCode.DUPLICATE_ACCOUNT_NO);
        }

        // ToDo: appKey, secretKey 유효성 검증

        Account account = Account.createAccount(
                userId, request.appKey(), request.secretKey(), request.accountNo(), accountNoHash,
                request.accountType());

        try {
            return accountCommandRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(AccountErrorCode.DUPLICATE_ACCOUNT_REQUEST);
        }

    }
}
