package com.sajo.user_service.account.client;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.client.dto.response.AccessTokenRevokeResponse;
import com.sajo.user_service.account.client.dto.response.KisAccessTokenResponse;
import com.sajo.user_service.account.client.dto.response.KisApprovalKeyResponse;
import com.sajo.user_service.account.client.dto.response.KisBalanceResponse;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.exception.AccountErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KisClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private KisClient client;

    private void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KisClient(builder, new KisApiProperties("https://kis.example", "https://kis-real.example"));
    }

    @Test
    @DisplayName("정상 응답이면 AccessTokenResponse를 반환하고, VIRTUAL은 virtual 서버로 요청한다")
    void getsAccessTokenSuccessfully() {
        // given
        setUp();
        server.expect(requestTo("https://kis.example/oauth2/tokenP"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.grant_type").value("client_credentials"))
                .andExpect(jsonPath("$.appkey").value("app-key"))
                .andExpect(jsonPath("$.appsecret").value("secret-key"))
                .andRespond(withSuccess("""
                        {"access_token":"issued-token","token_type":"Bearer","expires_in":86400,"access_token_token_expired":"2026-01-01 00:00:00"}
                        """, MediaType.APPLICATION_JSON));

        // when
        KisAccessTokenResponse response = client.getAccessToken("app-key", "secret-key", AccountType.VIRTUAL);

        // then
        assertThat(response.access_token()).isEqualTo("issued-token");
        server.verify();
    }

    @Test
    @DisplayName("REAL 계좌는 real 서버로 요청한다")
    void routesRealAccountTypeToRealServer() {
        // given
        setUp();
        server.expect(requestTo("https://kis-real.example/oauth2/tokenP"))
                .andRespond(withSuccess("""
                        {"access_token":"issued-token","token_type":"Bearer","expires_in":86400,"access_token_token_expired":"2026-01-01 00:00:00"}
                        """, MediaType.APPLICATION_JSON));

        // when
        client.getAccessToken("app-key", "secret-key", AccountType.REAL);

        // then
        server.verify();
    }

    @Test
    @DisplayName("4xx 응답이면 INVALID_KIS_CREDENTIALS 예외를 던지고 원본 KIS 메시지는 노출하지 않는다")
    void convertsKisHttp4xxToInvalidCredentials() {
        // given
        setUp();
        server.expect(requestTo("https://kis.example/oauth2/tokenP"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error_code":"EGW00103","error_description":"유효하지 않은 AppKey입니다."}
                                """));

        // when & then
        assertThatThrownBy(() -> client.getAccessToken("app-key", "secret-key", AccountType.VIRTUAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.INVALID_KIS_CREDENTIALS);
                    assertThat(businessException.getMessage())
                            .isEqualTo(AccountErrorCode.INVALID_KIS_CREDENTIALS.getMessage())
                            .doesNotContain("EGW00103", "유효하지 않은 AppKey입니다");
                });

        server.verify();
    }

    @Test
    @DisplayName("KIS rate limit(EGW00133) 응답이면 INVALID_KIS_CREDENTIALS가 아닌 KIS_RATE_LIMITED 예외를 던진다")
    void convertsKisRateLimitToKisRateLimited() {
        // given
        setUp();
        server.expect(requestTo("https://kis.example/oauth2/tokenP"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error_code":"EGW00133","error_description":"접근토큰 발급 잠시 후 다시 시도하세요(1분당 1회)"}
                                """));

        // when & then
        assertThatThrownBy(() -> client.getAccessToken("app-key", "secret-key", AccountType.VIRTUAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.KIS_RATE_LIMITED);
                });

        server.verify();
    }

    @Test
    @DisplayName("KIS rate limit(EGW00201, 초당 거래건수 초과) 응답이면 KIS_RATE_LIMITED 예외를 던진다")
    void convertsKisPerSecondRateLimitToKisRateLimited() {
        // given
        setUp();
        server.expect(requestTo("https://kis.example/oauth2/tokenP"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error_code":"EGW00201","error_description":"초당 거래건수를 초과하였습니다."}
                                """));

        // when & then
        assertThatThrownBy(() -> client.getAccessToken("app-key", "secret-key", AccountType.VIRTUAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.KIS_RATE_LIMITED);
                });

        server.verify();
    }

    @Test
    @DisplayName("5xx 응답이면 KIS_TOKEN_ISSUE_FAILED 예외를 던진다")
    void convertsKisHttp5xxToTokenIssueFailed() {
        // given
        setUp();
        server.expect(requestTo("https://kis.example/oauth2/tokenP"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        // when & then
        assertThatThrownBy(() -> client.getAccessToken("app-key", "secret-key", AccountType.VIRTUAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.KIS_TOKEN_ISSUE_FAILED);
                });

        server.verify();
    }

    @Test
    @DisplayName("응답 바디가 비어 있으면 KIS_TOKEN_ISSUE_FAILED 예외를 던진다")
    void throwsExceptionWhenResponseBodyIsEmpty() {
        // given
        setUp();
        server.expect(requestTo("https://kis.example/oauth2/tokenP"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.getAccessToken("app-key", "secret-key", AccountType.VIRTUAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.KIS_TOKEN_ISSUE_FAILED);
                });

        server.verify();
    }

    @Test
    @DisplayName("접속키 발급 - 정상 응답이면 approval_key를 반환하고, 접근토큰과 다른 body 필드명(secretkey)을 사용한다")
    void getsApprovalKeySuccessfully() {
        // given
        setUp();
        server.expect(requestTo("https://kis.example/oauth2/Approval"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.grant_type").value("client_credentials"))
                .andExpect(jsonPath("$.appkey").value("app-key"))
                .andExpect(jsonPath("$.secretkey").value("secret-key"))
                .andRespond(withSuccess("""
                        {"approval_key":"issued-approval-key"}
                        """, MediaType.APPLICATION_JSON));

        // when
        KisApprovalKeyResponse response = client.getApprovalKey("app-key", "secret-key", AccountType.VIRTUAL);

        // then
        assertThat(response.approval_key()).isEqualTo("issued-approval-key");
        server.verify();
    }

    @Test
    @DisplayName("접속키 발급 - 4xx 응답이면 INVALID_KIS_CREDENTIALS 예외를 던진다")
    void convertsApprovalKeyHttp4xxToInvalidCredentials() {
        // given
        setUp();
        server.expect(requestTo("https://kis.example/oauth2/Approval"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error_code":"EGW00103","error_description":"유효하지 않은 AppKey입니다."}
                                """));

        // when & then
        assertThatThrownBy(() -> client.getApprovalKey("app-key", "secret-key", AccountType.VIRTUAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.INVALID_KIS_CREDENTIALS);
                });

        server.verify();
    }

    @Test
    @DisplayName("토큰 폐기 - 정상 응답이면 code/message를 반환한다")
    void revokesAccessTokenSuccessfully() {
        // given
        setUp();
        server.expect(requestTo("https://kis.example/oauth2/revokeP"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.appkey").value("app-key"))
                .andExpect(jsonPath("$.appsecret").value("secret-key"))
                .andExpect(jsonPath("$.token").value("issued-token"))
                .andRespond(withSuccess("""
                        {"code":"200","message":"접근토큰 폐기에 성공하였습니다"}
                        """, MediaType.APPLICATION_JSON));

        // when
        AccessTokenRevokeResponse response =
                client.revokeAccessToken("app-key", "secret-key", "issued-token", AccountType.VIRTUAL);

        // then
        assertThat(response.code()).isEqualTo("200");
        server.verify();
    }

    @Test
    @DisplayName("토큰 폐기 - 4xx 응답이면 INVALID_KIS_CREDENTIALS 예외를 던진다")
    void revokeAccessTokenFailsWithHttp4xx() {
        // given
        setUp();
        server.expect(requestTo("https://kis.example/oauth2/revokeP"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error_code":"EGW00121","error_description":"유효하지 않은 token 입니다."}
                                """));

        // when & then
        assertThatThrownBy(() ->
                client.revokeAccessToken("app-key", "secret-key", "issued-token", AccountType.VIRTUAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.INVALID_KIS_CREDENTIALS);
                });

        server.verify();
    }

    @Test
    @DisplayName("잔고조회(예수금) - 정상 응답이면 KisBalanceResponse를 반환하고, VIRTUAL은 virtual 서버/모의 tr_id로 요청한다")
    void inquiresBalanceSuccessfully() {
        // given
        setUp();
        server.expect(requestTo("https://kis.example/uapi/domestic-stock/v1/trading/inquire-balance"
                        + "?CANO=12345678&ACNT_PRDT_CD=01&AFHR_FLPR_YN=N&OFL_YN=&INQR_DVSN=02&UNPR_DVSN=01"
                        + "&FUND_STTL_ICLD_YN=N&FNCG_AMT_AUTO_RDPT_YN=N&PRCS_DVSN=00"
                        + "&CTX_AREA_FK100=&CTX_AREA_NK100="))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("authorization", "Bearer issued-token"))
                .andExpect(header("appkey", "app-key"))
                .andExpect(header("appsecret", "secret-key"))
                .andExpect(header("tr_id", "VTTC8434R"))
                .andExpect(header("tr_cont", ""))
                .andRespond(withSuccess("""
                        {"rt_cd":"0","msg_cd":"MSG_CD","msg1":"정상처리 되었습니다","ctx_area_fk100":"","ctx_area_nk100":"",
                         "output1":[],"output2":[{"dnca_tot_amt":"1000000"}]}
                        """, MediaType.APPLICATION_JSON));

        // when
        KisBalanceResponse response = client.inquireBalance(
                "issued-token", "app-key", "secret-key", "12345678", "01", AccountType.VIRTUAL);

        // then
        assertThat(response.rt_cd()).isEqualTo("0");
        assertThat(response.output2()).hasSize(1);
        assertThat(response.output2().getFirst().dnca_tot_amt()).isEqualTo("1000000");
        server.verify();
    }

    @Test
    @DisplayName("잔고조회 - REAL 계좌는 real 서버와 실전 tr_id로 요청한다")
    void inquiresBalanceRoutesRealAccountTypeToRealServer() {
        // given
        setUp();
        server.expect(requestTo("https://kis-real.example/uapi/domestic-stock/v1/trading/inquire-balance"
                        + "?CANO=12345678&ACNT_PRDT_CD=01&AFHR_FLPR_YN=N&OFL_YN=&INQR_DVSN=02&UNPR_DVSN=01"
                        + "&FUND_STTL_ICLD_YN=N&FNCG_AMT_AUTO_RDPT_YN=N&PRCS_DVSN=00"
                        + "&CTX_AREA_FK100=&CTX_AREA_NK100="))
                .andExpect(header("tr_id", "TTTC8434R"))
                .andRespond(withSuccess("""
                        {"rt_cd":"0","msg_cd":"MSG_CD","msg1":"정상처리 되었습니다","output1":[],"output2":[{}]}
                        """, MediaType.APPLICATION_JSON));

        // when
        client.inquireBalance("issued-token", "app-key", "secret-key", "12345678", "01", AccountType.REAL);

        // then
        server.verify();
    }

    @Test
    @DisplayName("잔고조회 - rt_cd가 0이 아니면 HTTP 200이어도 KIS_BALANCE_INQUIRY_FAILED 예외를 던진다")
    void inquireBalanceFailsWhenRtCdIsNotZero() {
        // given
        setUp();
        server.expect(requestTo("https://kis.example/uapi/domestic-stock/v1/trading/inquire-balance"
                        + "?CANO=12345678&ACNT_PRDT_CD=01&AFHR_FLPR_YN=N&OFL_YN=&INQR_DVSN=02&UNPR_DVSN=01"
                        + "&FUND_STTL_ICLD_YN=N&FNCG_AMT_AUTO_RDPT_YN=N&PRCS_DVSN=00"
                        + "&CTX_AREA_FK100=&CTX_AREA_NK100="))
                .andRespond(withSuccess("""
                        {"rt_cd":"1","msg_cd":"MSG_CD","msg1":"조회 실패","output1":[],"output2":[]}
                        """, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.inquireBalance(
                "issued-token", "app-key", "secret-key", "12345678", "01", AccountType.VIRTUAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.KIS_BALANCE_INQUIRY_FAILED);
                });

        server.verify();
    }

    @Test
    @DisplayName("잔고조회 - 5xx 응답이면 KIS_BALANCE_INQUIRY_FAILED 예외를 던진다")
    void inquireBalanceFailsWithHttp5xx() {
        // given
        setUp();
        server.expect(requestTo("https://kis.example/uapi/domestic-stock/v1/trading/inquire-balance"
                        + "?CANO=12345678&ACNT_PRDT_CD=01&AFHR_FLPR_YN=N&OFL_YN=&INQR_DVSN=02&UNPR_DVSN=01"
                        + "&FUND_STTL_ICLD_YN=N&FNCG_AMT_AUTO_RDPT_YN=N&PRCS_DVSN=00"
                        + "&CTX_AREA_FK100=&CTX_AREA_NK100="))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        // when & then
        assertThatThrownBy(() -> client.inquireBalance(
                "issued-token", "app-key", "secret-key", "12345678", "01", AccountType.VIRTUAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.KIS_BALANCE_INQUIRY_FAILED);
                });

        server.verify();
    }

    @Test
    @DisplayName("잔고조회(연속조회, 최초 호출) - 커서 없이 호출하면 tr_cont 공백/CTX_AREA 공란으로 요청하고, "
            + "응답 tr_cont가 M이면 hasNext=true를 반환한다")
    void inquiresBalanceFirstPageHasNext() {
        // given
        setUp();
        server.expect(requestTo("https://kis.example/uapi/domestic-stock/v1/trading/inquire-balance"
                        + "?CANO=12345678&ACNT_PRDT_CD=01&AFHR_FLPR_YN=N&OFL_YN=&INQR_DVSN=02&UNPR_DVSN=01"
                        + "&FUND_STTL_ICLD_YN=N&FNCG_AMT_AUTO_RDPT_YN=N&PRCS_DVSN=00"
                        + "&CTX_AREA_FK100=&CTX_AREA_NK100="))
                .andExpect(header("tr_cont", ""))
                .andRespond(withSuccess("""
                        {"rt_cd":"0","msg_cd":"MSG_CD","msg1":"정상처리 되었습니다","ctx_area_fk100":"next-fk",
                         "ctx_area_nk100":"next-nk","output1":[{"pdno":"005930"}],"output2":[{}]}
                        """, MediaType.APPLICATION_JSON)
                        .header("tr_cont", "M"));

        // when
        KisContinuationResult<KisBalanceResponse> result = client.inquireBalance(
                "issued-token", "app-key", "secret-key", "12345678", "01", AccountType.VIRTUAL, null, null);

        // then
        assertThat(result.hasNext()).isTrue();
        assertThat(result.body().output1()).hasSize(1);
        assertThat(result.body().ctx_area_fk100()).isEqualTo("next-fk");
        assertThat(result.body().ctx_area_nk100()).isEqualTo("next-nk");
        server.verify();
    }

    @Test
    @DisplayName("잔고조회(연속조회, 다음 페이지) - 커서를 넘기면 tr_cont=N과 그 커서 값으로 요청하고, "
            + "응답 tr_cont가 D면 hasNext=false를 반환한다")
    void inquiresBalanceNextPageHasNoNext() {
        // given
        setUp();
        server.expect(requestTo("https://kis.example/uapi/domestic-stock/v1/trading/inquire-balance"
                        + "?CANO=12345678&ACNT_PRDT_CD=01&AFHR_FLPR_YN=N&OFL_YN=&INQR_DVSN=02&UNPR_DVSN=01"
                        + "&FUND_STTL_ICLD_YN=N&FNCG_AMT_AUTO_RDPT_YN=N&PRCS_DVSN=00"
                        + "&CTX_AREA_FK100=prev-fk&CTX_AREA_NK100=prev-nk"))
                .andExpect(header("tr_cont", "N"))
                .andRespond(withSuccess("""
                        {"rt_cd":"0","msg_cd":"MSG_CD","msg1":"정상처리 되었습니다","output1":[],"output2":[{}]}
                        """, MediaType.APPLICATION_JSON)
                        .header("tr_cont", "D"));

        // when
        KisContinuationResult<KisBalanceResponse> result = client.inquireBalance(
                "issued-token", "app-key", "secret-key", "12345678", "01", AccountType.VIRTUAL,
                "prev-fk", "prev-nk");

        // then
        assertThat(result.hasNext()).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("타임아웃/연결 실패 시 KIS_TOKEN_ISSUE_FAILED 예외로 변환하고 원본 메시지는 노출하지 않는다")
    void convertsConnectionFailureToBusinessException() {
        // given
        setUp();
        server.expect(requestTo("https://kis.example/oauth2/tokenP"))
                .andRespond(request -> {
                    throw new ResourceAccessException("KIS connection timed out");
                });

        // when & then
        assertThatThrownBy(() -> client.getAccessToken("app-key", "secret-key", AccountType.VIRTUAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.KIS_TOKEN_ISSUE_FAILED);
                    assertThat(businessException.getMessage())
                            .isEqualTo(AccountErrorCode.KIS_TOKEN_ISSUE_FAILED.getMessage());
                });

        server.verify();
    }
}
