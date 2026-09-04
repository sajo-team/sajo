package com.sajo.user_service.account.client;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.client.dto.request.AccessTokenRequest;
import com.sajo.user_service.account.client.dto.request.AccessTokenRevokeRequest;
import com.sajo.user_service.account.client.dto.request.ApprovalKeyRequest;
import com.sajo.user_service.account.client.dto.response.AccessTokenRevokeResponse;
import com.sajo.user_service.account.client.dto.response.KisAccessTokenResponse;
import com.sajo.user_service.account.client.dto.response.KisApprovalKeyResponse;
import com.sajo.user_service.account.client.dto.response.KisBalanceResponse;
import com.sajo.user_service.account.client.dto.response.KisErrorResponse;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.exception.AccountErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Set;
import java.util.function.Supplier;

@Slf4j
@Component
public class KisClient {

    private static final String GRANT_TYPE = "client_credentials";
    private static final String ACCESS_TOKEN_PATH = "/oauth2/tokenP";
    private static final String APPROVAL_KEY_PATH = "/oauth2/Approval";
    private static final String ACCESS_TOKEN_REVOKE = "/oauth2/revokeP";
    private static final String INQUIRE_BALANCE = "/uapi/domestic-stock/v1/trading/inquire-balance";
    private static final String BALANCE_TR_ID_REAL = "TTTC8434R";
    private static final String BALANCE_TR_ID_VIRTUAL = "VTTC8434R";

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

    // kis 주식 잔고 조회 요청
    public KisBalanceResponse inquireBalance(
            String accessToken, String appKey, String secretKey, String cano, String accountProductCode,
            AccountType accountType
    ) {
        RestClient restClient = selectRestClient(accountType);
        String trId = accountType == AccountType.REAL ? BALANCE_TR_ID_REAL : BALANCE_TR_ID_VIRTUAL;

        String uri = UriComponentsBuilder.fromPath(INQUIRE_BALANCE)
                .queryParam("CANO", cano) // 종합계좌번호 - 계좌번호 체계(8-2)의 앞 8자리
                .queryParam("ACNT_PRDT_CD", accountProductCode) // 계좌상품코드 - 계좌번호 체계(8-2)의 뒤 2자리
                .queryParam("AFHR_FLPR_YN", "N") // 시간외단일가/거래소여부 - N: 기본값, Y: 시간외단일가, X: NXT 정규장
                .queryParam("OFL_YN", "") // 오프라인여부 - 공란(Default)
                .queryParam("INQR_DVSN", "02") // 조회구분 - 01: 대출일별, 02: 종목별
                .queryParam("UNPR_DVSN", "01") // 단가구분 - 01: 기본값
                .queryParam("FUND_STTL_ICLD_YN", "N") // 펀드결제분포함여부 - N: 포함하지 않음, Y: 포함
                .queryParam("FNCG_AMT_AUTO_RDPT_YN", "N") // 융자금액자동상환여부 - N: 기본값
                .queryParam("PRCS_DVSN", "00") // 처리구분 - 00: 전일매매포함, 01: 전일매매미포함
                .queryParam("CTX_AREA_FK100", "") // 연속조회검색조건100 - 공란: 최초 조회시
                .queryParam("CTX_AREA_NK100", "") // 연속조회키100 - 공란: 최초 조회시
                .build()
                .toUriString();

        // 요청
        KisBalanceResponse response =
                inquire(restClient, uri, accessToken, appKey, secretKey, trId, KisBalanceResponse.class);

        // KIS 조회 API는 HTTP 200이어도 rt_cd가 "0"이 아니면 업무상 실패
        if (!"0".equals(response.rt_cd())) {
            log.warn("KIS 잔고조회 실패. msg_cd={}, msg1={}", response.msg_cd(), response.msg1());
            throw new BusinessException(AccountErrorCode.KIS_BALANCE_INQUIRY_FAILED);
        }
        return response;
    }

    private <T> T inquire(
            RestClient restClient, String uri, String accessToken, String appKey, String secretKey, String trId,
            Class<T> responseType
    ) {
        return execute(() -> restClient.get()
                .uri(uri)
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", secretKey)
                .header("tr_id", trId)
                .retrieve()
                .body(responseType), AccountErrorCode.KIS_BALANCE_INQUIRY_FAILED);
    }

    private <T> T issue(RestClient restClient, String path, Object request, Class<T> responseType) {
        return execute(() -> restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(responseType), AccountErrorCode.KIS_TOKEN_ISSUE_FAILED);
    }

    // issue()/inquire() 공통 에러 처리 - 실제 요청 실행만 각자 넘기고, 실패 시 판별/변환은 여기서 한다
    private <T> T execute(Supplier<T> request, AccountErrorCode defaultFailureCode) {
        T response;
        try {
            response = request.get();
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
            throw new BusinessException(defaultFailureCode);

        } catch (RestClientException e) {
            // 타임아웃/연결 실패 등 HTTP 응답 자체를 못 받은 경우
            log.warn("KIS 요청 중 네트워크 오류 발생. message={}", e.getMessage(), e);
            throw new BusinessException(defaultFailureCode);
        }

        if (response == null) {
            throw new BusinessException(defaultFailureCode, "KIS 응답이 비어 있습니다.");
        }
        return response;
    }

    private RestClient selectRestClient(AccountType accountType) {
        return accountType == AccountType.REAL ? realRestClient : virtualRestClient;
    }

}
