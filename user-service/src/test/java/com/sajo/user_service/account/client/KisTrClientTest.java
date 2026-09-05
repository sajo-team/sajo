package com.sajo.user_service.account.client;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.client.dto.response.KisBalanceResponse;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.exception.AccountErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KisTrClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private KisTrClient client;

    private void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KisTrClient(builder, new KisApiProperties("https://kis.example", "https://kis-real.example"));
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
    @DisplayName("잔고조회 - HTTP 200이어도 msg_cd가 EGW00201(초당 거래건수 초과)이면 "
            + "KIS_BALANCE_INQUIRY_FAILED가 아닌 KIS_RATE_LIMITED 예외를 던진다")
    void inquireBalanceFailsWithRateLimitWhenRtCdIsNotZero() {
        // given
        setUp();
        server.expect(requestTo("https://kis.example/uapi/domestic-stock/v1/trading/inquire-balance"
                        + "?CANO=12345678&ACNT_PRDT_CD=01&AFHR_FLPR_YN=N&OFL_YN=&INQR_DVSN=02&UNPR_DVSN=01"
                        + "&FUND_STTL_ICLD_YN=N&FNCG_AMT_AUTO_RDPT_YN=N&PRCS_DVSN=00"
                        + "&CTX_AREA_FK100=&CTX_AREA_NK100="))
                .andRespond(withSuccess("""
                        {"rt_cd":"1","msg_cd":"EGW00201","msg1":"초당 거래건수를 초과하였습니다.","output1":[],"output2":[]}
                        """, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.inquireBalance(
                "issued-token", "app-key", "secret-key", "12345678", "01", AccountType.VIRTUAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.KIS_RATE_LIMITED);
                });

        server.verify();
    }

    @Test
    @DisplayName("잔고조회 - 4xx 응답 바디가 oauth 에러 포맷(error_code)이 아니어도(rt_cd/msg_cd 포맷) "
            + "NPE 없이 INVALID_KIS_CREDENTIALS 예외를 던진다")
    void inquireBalanceFailsWithHttp4xxInDifferentErrorShape() {
        // given
        setUp();
        server.expect(requestTo("https://kis.example/uapi/domestic-stock/v1/trading/inquire-balance"
                        + "?CANO=12345678&ACNT_PRDT_CD=01&AFHR_FLPR_YN=N&OFL_YN=&INQR_DVSN=02&UNPR_DVSN=01"
                        + "&FUND_STTL_ICLD_YN=N&FNCG_AMT_AUTO_RDPT_YN=N&PRCS_DVSN=00"
                        + "&CTX_AREA_FK100=&CTX_AREA_NK100="))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"rt_cd":"1","msg_cd":"EGW00123","msg1":"유효하지 않은 토큰입니다."}
                                """));

        // when & then
        assertThatThrownBy(() -> client.inquireBalance(
                "issued-token", "app-key", "secret-key", "12345678", "01", AccountType.VIRTUAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.INVALID_KIS_CREDENTIALS);
                });

        server.verify();
    }

    @Test
    @DisplayName("잔고조회 - 4xx 응답 바디가 msg_cd 포맷(TR API)으로 EGW00201을 내려주면 "
            + "INVALID_KIS_CREDENTIALS가 아닌 KIS_RATE_LIMITED 예외를 던진다")
    void inquireBalanceFailsWithRateLimitInHttpExceptionWithTrErrorShape() {
        // given
        setUp();
        server.expect(requestTo("https://kis.example/uapi/domestic-stock/v1/trading/inquire-balance"
                        + "?CANO=12345678&ACNT_PRDT_CD=01&AFHR_FLPR_YN=N&OFL_YN=&INQR_DVSN=02&UNPR_DVSN=01"
                        + "&FUND_STTL_ICLD_YN=N&FNCG_AMT_AUTO_RDPT_YN=N&PRCS_DVSN=00"
                        + "&CTX_AREA_FK100=&CTX_AREA_NK100="))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"rt_cd":"1","msg_cd":"EGW00201","msg1":"초당 거래건수를 초과하였습니다."}
                                """));

        // when & then
        assertThatThrownBy(() -> client.inquireBalance(
                "issued-token", "app-key", "secret-key", "12345678", "01", AccountType.VIRTUAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.KIS_RATE_LIMITED);
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
    @DisplayName("잔고조회(연속조회) - 커서가 null이 아닌 빈 문자열이어도 최초 조회로 처리해 tr_cont 공백/CTX_AREA 공란으로 요청한다")
    void inquiresBalanceTreatsBlankCursorAsFirstCall() {
        // given
        setUp();
        server.expect(requestTo("https://kis.example/uapi/domestic-stock/v1/trading/inquire-balance"
                        + "?CANO=12345678&ACNT_PRDT_CD=01&AFHR_FLPR_YN=N&OFL_YN=&INQR_DVSN=02&UNPR_DVSN=01"
                        + "&FUND_STTL_ICLD_YN=N&FNCG_AMT_AUTO_RDPT_YN=N&PRCS_DVSN=00"
                        + "&CTX_AREA_FK100=&CTX_AREA_NK100="))
                .andExpect(header("tr_cont", ""))
                .andRespond(withSuccess("""
                        {"rt_cd":"0","msg_cd":"MSG_CD","msg1":"정상처리 되었습니다","output1":[],"output2":[{}]}
                        """, MediaType.APPLICATION_JSON));

        // when
        client.inquireBalance(
                "issued-token", "app-key", "secret-key", "12345678", "01", AccountType.VIRTUAL, "", "");

        // then
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
}
