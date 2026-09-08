package com.sajo.user_service.account.domain;

public enum EventType {
    TOKEN_ISSUE_SUCCESS, // access token 발급 성공
    TOKEN_ISSUE_FAILED, // access token 발급 실패
    TOKEN_REVOKE_SUCCESS, // access token 폐기 성공
    TOKEN_REVOKE_FAILED // access token 폐기 실패
}
