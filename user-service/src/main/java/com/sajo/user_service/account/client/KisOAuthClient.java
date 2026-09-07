package com.sajo.user_service.account.client;

import com.sajo.user_service.account.client.dto.request.AccessTokenRequest;
import com.sajo.user_service.account.client.dto.request.AccessTokenRevokeRequest;
import com.sajo.user_service.account.client.dto.request.ApprovalKeyRequest;
import com.sajo.user_service.account.client.dto.response.AccessTokenRevokeResponse;
import com.sajo.user_service.account.client.dto.response.KisAccessTokenResponse;
import com.sajo.user_service.account.client.dto.response.KisApprovalKeyResponse;
import com.sajo.user_service.account.client.dto.response.KisOAuthErrorResponse;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.exception.AccountErrorCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// kis oauth(접근토큰 발급/폐기, 웹소켓 접속키 발급) 전용 클라이언트
@Component
public class KisOAuthClient extends AbstractKisClient {

    private static final String GRANT_TYPE = "client_credentials";
    private static final String ACCESS_TOKEN_PATH = "/oauth2/tokenP";
    private static final String APPROVAL_KEY_PATH = "/oauth2/Approval";
    private static final String ACCESS_TOKEN_REVOKE = "/oauth2/revokeP";

    public KisOAuthClient(RestClient.Builder restClientBuilder, KisApiProperties properties) {
        super(restClientBuilder, properties);
    }

    // kis access token 발급 요청
    public KisAccessTokenResponse getAccessToken(String appKey, String secretKey, AccountType accountType) {
        RestClient restClient = selectRestClient(accountType);
        AccessTokenRequest request = new AccessTokenRequest(GRANT_TYPE, appKey, secretKey);
        return issue(restClient, ACCESS_TOKEN_PATH, request, KisAccessTokenResponse.class);
    }

    // kis 웹소켓 접속키(approval key) 발급 요청
    public KisApprovalKeyResponse getApprovalKey(String appKey, String secretKey, AccountType accountType) {
        RestClient restClient = selectRestClient(accountType);
        ApprovalKeyRequest request = new ApprovalKeyRequest(GRANT_TYPE, appKey, secretKey);
        return issue(restClient, APPROVAL_KEY_PATH, request, KisApprovalKeyResponse.class);
    }

    // kis access token 폐기 요청
    public AccessTokenRevokeResponse revokeAccessToken(
            String appKey, String secretKey, String token, AccountType accountType
    ) {
        RestClient restClient = selectRestClient(accountType);
        AccessTokenRevokeRequest request = new AccessTokenRevokeRequest(appKey, secretKey, token);
        return issue(restClient, ACCESS_TOKEN_REVOKE, request, AccessTokenRevokeResponse.class);
    }

    private <T> T issue(RestClient restClient, String path, Object request, Class<T> responseType) {
        ResponseEntity<T> responseEntity = execute(() -> restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(responseType), KisOAuthErrorResponse.class, AccountErrorCode.KIS_TOKEN_ISSUE_FAILED);
        return responseEntity.getBody();
    }
}
