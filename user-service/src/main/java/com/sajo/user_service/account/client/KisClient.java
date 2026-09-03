package com.sajo.user_service.account.client;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.client.dto.request.AccessTokenRequest;
import com.sajo.user_service.account.client.dto.request.AccessTokenRevokeRequest;
import com.sajo.user_service.account.client.dto.request.ApprovalKeyRequest;
import com.sajo.user_service.account.client.dto.response.AccessTokenRevokeResponse;
import com.sajo.user_service.account.client.dto.response.KisAccessTokenResponse;
import com.sajo.user_service.account.client.dto.response.KisApprovalKeyResponse;
import com.sajo.user_service.account.client.dto.response.KisErrorResponse;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.exception.AccountErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Set;

@Slf4j
@Component
public class KisClient {

    private static final String GRANT_TYPE = "client_credentials";
    private static final String ACCESS_TOKEN_PATH = "/oauth2/tokenP";
    private static final String APPROVAL_KEY_PATH = "/oauth2/Approval";
    private static final String ACCESS_TOKEN_REVOKE = "/oauth2/revokeP";

    // EGW00133: 접근토큰 발급 rate limit(1분당 1회), EGW00201: 초당 거래건수 초과
    private static final Set<String> RATE_LIMIT_ERROR_CODES = Set.of("EGW00133", "EGW00201");

    private final RestClient virtualRestClient;
    private final RestClient realRestClient;

    public KisClient(RestClient.Builder restClientBuilder, KisApiProperties properties) {
        this.virtualRestClient = restClientBuilder.clone().baseUrl(properties.virtualBaseUrl()).build();
        this.realRestClient = restClientBuilder.clone().baseUrl(properties.realBaseUrl()).build();
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
        T response;
        try {
            response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientResponseException e) {
            KisErrorResponse kisError;

            try {
                kisError = e.getResponseBodyAs(KisErrorResponse.class);
            } catch (RestClientException parseEx) {
                log.warn("KIS 에러 응답 파싱 실패. body={}", e.getResponseBodyAsString());
                kisError = null;
            }
            String message = kisError != null ? kisError.error_description() : e.getMessage();

            log.warn("KIS 요청 실패. status={}, errorCode={}, message={}",
                    e.getStatusCode(), kisError != null ? kisError.error_code() : null, message);

            // rate limit은 자격증명 문제가 아니므로 별도로 구분
            if (kisError != null && RATE_LIMIT_ERROR_CODES.contains(kisError.error_code())) {
                throw new BusinessException(AccountErrorCode.KIS_RATE_LIMITED);
            }

            // 4xx는 우리가 보낸 요청/자격증명 문제, 5xx(+그 외)는 KIS 쪽 장애
            if (e.getStatusCode().is4xxClientError()) {
                throw new BusinessException(AccountErrorCode.INVALID_KIS_CREDENTIALS);
            }
            throw new BusinessException(AccountErrorCode.KIS_TOKEN_ISSUE_FAILED);

        } catch (RestClientException e) {
            // 타임아웃/연결 실패 등 HTTP 응답 자체를 못 받은 경우
            log.warn("KIS 요청 중 네트워크 오류 발생. message={}", e.getMessage(), e);
            throw new BusinessException(AccountErrorCode.KIS_TOKEN_ISSUE_FAILED);
        }

        if (response == null) {
            throw new BusinessException(
                    AccountErrorCode.KIS_TOKEN_ISSUE_FAILED,
                    "KIS 응답이 비어 있습니다."
            );
        }
        return response;
    }

    private RestClient selectRestClient(AccountType accountType) {
        return accountType == AccountType.REAL ? realRestClient : virtualRestClient;
    }

}
