package com.sajo.user_service.account.client;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.client.dto.request.AccessTokenRequest;
import com.sajo.user_service.account.client.dto.response.AccessTokenResponse;
import com.sajo.user_service.account.client.dto.response.KisErrorResponse;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.exception.AccountErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class KisClient {

    private static final String GRANT_TYPE = "client_credentials";
    private static final String ACCESS_TOKEN_PATH = "/oauth2/tokenP";

    private final RestClient virtualRestClient;
    private final RestClient realRestClient;

    public KisClient(RestClient.Builder restClientBuilder, KisApiProperties properties) {
        this.virtualRestClient = restClientBuilder.clone().baseUrl(properties.virtualBaseUrl()).build();
        this.realRestClient = restClientBuilder.clone().baseUrl(properties.realBaseUrl()).build();
    }

    // kis access token 발급 요청
    public AccessTokenResponse getAccessToken(String appKey, String secretKey, AccountType accountType) {
        RestClient restClient = selectRestClient(accountType);
        AccessTokenRequest request = new AccessTokenRequest(GRANT_TYPE, appKey, secretKey);

        AccessTokenResponse response;
        try {
            response = restClient.post()
                    .uri(ACCESS_TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AccessTokenResponse.class);
        } catch (RestClientResponseException e) {
            KisErrorResponse kisError;
            try {
                kisError = e.getResponseBodyAs(KisErrorResponse.class);
            } catch (RestClientException parseEx) {
                log.warn("KIS 에러 응답 파싱 실패. body={}", e.getResponseBodyAsString());
                kisError = null;
            }
            String message = kisError != null ? kisError.error_description() : e.getMessage();

            log.warn("KIS 토큰 발급 실패. status={}, errorCode={}, message={}",
                    e.getStatusCode(), kisError != null ? kisError.error_code() : null, message);

            // 4xx는 우리가 보낸 요청/자격증명 문제, 5xx(+그 외)는 KIS 쪽 장애
            if (e.getStatusCode().is4xxClientError()) {
                throw new BusinessException(AccountErrorCode.INVALID_KIS_CREDENTIALS);
            }
            throw new BusinessException(AccountErrorCode.KIS_TOKEN_ISSUE_FAILED);

        } catch (RestClientException e) {
            // 타임아웃/연결 실패 등 HTTP 응답 자체를 못 받은 경우
            log.warn("KIS 토큰 발급 중 네트워크 오류 발생. message={}", e.getMessage(), e);
            throw new BusinessException(AccountErrorCode.KIS_TOKEN_ISSUE_FAILED);
        }

        if (response == null) {
            throw new BusinessException(
                    AccountErrorCode.KIS_TOKEN_ISSUE_FAILED,
                    "KIS 토큰 발급 응답이 비어 있습니다."
            );
        }
        return response;
    }

    private RestClient selectRestClient(AccountType accountType) {
        return accountType == AccountType.REAL ? realRestClient : virtualRestClient;
    }

}
