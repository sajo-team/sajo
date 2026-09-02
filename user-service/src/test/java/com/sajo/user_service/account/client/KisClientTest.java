package com.sajo.user_service.account.client;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.exception.AccountErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;

class KisClientTest {

    @Test
    @DisplayName("타임아웃/연결 실패 시 KIS_TOKEN_ISSUE_FAILED 예외로 변환한다")
    void convertsConnectionFailureToBusinessException() {
        // given
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisClient client = new KisClient(builder, new KisApiProperties("https://kis.example", "https://kis-real.example"));

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
                });

        server.verify();
    }
}
