package com.sajo.user_service.account.client;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.client.dto.response.KisErrorInfo;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.exception.AccountErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Set;
import java.util.function.Supplier;

// KisOAuthClient/KisTrClient 공통 - REST 호출 실행, KIS 응답 실패(rate limit/자격증명/장애) 판별/변환을 담당한다
@Slf4j
abstract class AbstractKisClient {

    // EGW00133: 접근토큰 발급 rate limit(1분당 1회), EGW00201: 초당 거래건수 초과
    private static final Set<String> RATE_LIMIT_ERROR_CODES = Set.of("EGW00133", "EGW00201");

    private final RestClient virtualRestClient;
    private final RestClient realRestClient;

    protected AbstractKisClient(RestClient.Builder restClientBuilder, KisApiProperties properties) {
        this.virtualRestClient = restClientBuilder.clone().baseUrl(properties.virtualBaseUrl()).build();
        this.realRestClient = restClientBuilder.clone().baseUrl(properties.realBaseUrl()).build();
    }

    protected RestClient selectRestClient(AccountType accountType) {
        return accountType == AccountType.REAL ? realRestClient : virtualRestClient;
    }

    // 실제 요청 실행만 각자 넘기고, 실패 시 판별/변환은 여기서 한다
    // errorType: 호출자가 기대하는 KIS 에러 응답 shape (oauth 계열은 KisOAuthErrorResponse, tr 계열은 KisTrErrorResponse)
    protected <T, E extends KisErrorInfo> ResponseEntity<T> execute(
            Supplier<ResponseEntity<T>> request, Class<E> errorType, AccountErrorCode defaultFailureCode
    ) {
        ResponseEntity<T> response;
        try {
            response = request.get();
        } catch (RestClientResponseException e) {
            KisErrorInfo kisError;

            try {
                kisError = e.getResponseBodyAs(errorType);
            } catch (RestClientException parseEx) {
                log.warn("KIS 에러 응답 파싱 실패. body={}", e.getResponseBodyAsString());
                kisError = null;
            }

            String errorCode = kisError != null ? kisError.code() : null;
            String message = kisError != null ? kisError.message() : e.getMessage();

            log.warn("KIS 요청 실패. status={}, errorCode={}, message={}, body={}",
                    e.getStatusCode(), errorCode, message, e.getResponseBodyAsString());

            // rate limit은 자격증명 문제가 아니므로 별도로 구분
            if (errorCode != null && RATE_LIMIT_ERROR_CODES.contains(errorCode)) {
                throw new BusinessException(AccountErrorCode.KIS_RATE_LIMITED);
            }

            // 4xx는 우리가 보낸 요청/자격증명 문제, 5xx(+그 외)는 KIS 쪽 장애
            if (e.getStatusCode().is4xxClientError()) {
                throw new BusinessException(AccountErrorCode.INVALID_KIS_CREDENTIALS);
            }
            throw new BusinessException(defaultFailureCode);

        } catch (RestClientException e) {
            // 타임아웃/연결 실패 등 HTTP 응답 자체를 못 받은 경우
            log.warn("KIS 요청 중 네트워크 오류 발생. message={}", e.getMessage(), e);
            throw new BusinessException(defaultFailureCode);
        }

        if (response == null || response.getBody() == null) {
            throw new BusinessException(defaultFailureCode, "KIS 응답이 비어 있습니다.");
        }
        return response;
    }

    protected static boolean isRateLimitCode(String code) {
        return code != null && RATE_LIMIT_ERROR_CODES.contains(code);
    }
}
