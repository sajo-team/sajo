package com.sajo.user_service.auth.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.auth.controller.dto.request.SignUpRequest;
import com.sajo.user_service.auth.domain.User;
import com.sajo.user_service.auth.exception.UserErrorCode;
import com.sajo.user_service.auth.repository.command.UserCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserCommandService {

    private final UserCommandRepository userCommandRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User signUp(SignUpRequest request) {
        if (userCommandRepository.existsByEmail(request.email())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.of(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name()
        );

        try{
        return userCommandRepository.saveAndFlush(user);
        }catch (DataIntegrityViolationException e) {
            throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
    }

    }
}
