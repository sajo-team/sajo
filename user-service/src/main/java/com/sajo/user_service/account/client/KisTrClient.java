package com.sajo.user_service.account.client;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.client.dto.response.KisBalanceResponse;
import com.sajo.user_service.account.client.dto.response.KisTrErrorResponse;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.exception.AccountErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

// kis tr(잔고조회 등 거래 조회) 전용 클라이언트
@Slf4j
@Component
public class KisTrClient extends AbstractKisClient {

    private static final String INQUIRE_BALANCE = "/uapi/domestic-stock/v1/trading/inquire-balance";
    private static final String BALANCE_TR_ID_REAL = "TTTC8434R";
    private static final String BALANCE_TR_ID_VIRTUAL = "VTTC8434R";

    public KisTrClient(RestClient.Builder restClientBuilder, KisApiProperties properties) {
        super(restClientBuilder, properties);
    }

    // kis 주식 잔고 조회 요청 (예수금 전용 - 첫 페이지만, 보유종목 목록은 안 씀)
    public KisBalanceResponse inquireBalance(
            String accessToken, String appKey, String secretKey, String cano, String accountProductCode,
            AccountType accountType
    ) {
        return inquireBalance(
                accessToken, appKey, secretKey, cano, accountProductCode, accountType, null, null
        ).body();
    }

    // kis 주식 잔고 조회 요청 (보유종목 연속조회용) - ctxAreaFk100/ctxAreaNk100이 둘 다 null이면 최초 조회
    public KisContinuationResult<KisBalanceResponse> inquireBalance(
            String accessToken, String appKey, String secretKey, String cano, String accountProductCode,
            AccountType accountType, String ctxAreaFk100, String ctxAreaNk100
    ) {
        RestClient restClient = selectRestClient(accountType);
        String trId = accountType == AccountType.REAL ? BALANCE_TR_ID_REAL : BALANCE_TR_ID_VIRTUAL;
        boolean isFirstCall = ctxAreaFk100 == null && ctxAreaNk100 == null;
        String trCont = isFirstCall ? "" : "N"; // 공백: 초기 조회, N: 다음 데이터 조회

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
                .queryParam("CTX_AREA_FK100", isFirstCall ? "" : ctxAreaFk100) // 연속조회검색조건100
                .queryParam("CTX_AREA_NK100", isFirstCall ? "" : ctxAreaNk100) // 연속조회키100
                .build()
                .toUriString();

        // 요청
        KisContinuationResult<KisBalanceResponse> result =
                inquire(restClient, uri, accessToken, appKey, secretKey, trId, trCont, KisBalanceResponse.class);

        // KIS 조회 API는 HTTP 200이어도 rt_cd가 "0"이 아니면 업무상 실패
        KisBalanceResponse response = result.body();
        if (!"0".equals(response.rt_cd())) {
            log.warn("KIS 잔고조회 실패. msg_cd={}, msg1={}", response.msg_cd(), response.msg1());
            if (isRateLimitCode(response.msg_cd())) {
                throw new BusinessException(AccountErrorCode.KIS_RATE_LIMITED);
            }
            throw new BusinessException(AccountErrorCode.KIS_BALANCE_INQUIRY_FAILED);
        }
        return result;
    }

    private <T> KisContinuationResult<T> inquire(
            RestClient restClient, String uri, String accessToken, String appKey, String secretKey, String trId,
            String trCont, Class<T> responseType
    ) {
        ResponseEntity<T> responseEntity = execute(() -> restClient.get()
                .uri(uri)
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", secretKey)
                .header("tr_id", trId)
                .header("tr_cont", trCont)
                .retrieve()
                .toEntity(responseType), KisTrErrorResponse.class, AccountErrorCode.KIS_BALANCE_INQUIRY_FAILED);

        // 응답 헤더 tr_cont: F/M = 다음 데이터 있음, D/E = 마지막
        String responseTrCont = responseEntity.getHeaders().getFirst("tr_cont");
        boolean hasNext = "F".equals(responseTrCont) || "M".equals(responseTrCont);
        return new KisContinuationResult<>(responseEntity.getBody(), hasNext);
    }
}
