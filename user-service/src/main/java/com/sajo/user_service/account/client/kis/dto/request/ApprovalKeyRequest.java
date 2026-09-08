package com.sajo.user_service.account.client.kis.dto.request;

public record ApprovalKeyRequest(
        String grant_type,
        String appkey,
        String secretkey
) {
}
