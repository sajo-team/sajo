package com.other.feign;

import com.sajo.common.code.ErrorResponseCode;
import com.sajo.common.exception.BusinessException;
import com.sajo.common.feign.CommonFeignErrorDecoder;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CommonFeignErrorDecoder 테스트")
class CommonFeignErrorDecoderTest {

    private final CommonFeignErrorDecoder decoder = new CommonFeignErrorDecoder(new ObjectMapper());

    @Test
    @DisplayName("ErrorResponse 포맷 body면 원래 errorCode/message를 담은 BusinessException을 반환한다")
    void parsesErrorResponseBody() {
        String body = "{\"success\":false,\"errorCode\":\"ITEM_0001\",\"message\":\"아이템 없음\"}";
        Response response = Response.builder()
                .status(404)
                .request(Request.create(Request.HttpMethod.GET, "/items/1", Map.of(), new byte[0], StandardCharsets.UTF_8))
                .body(body, StandardCharsets.UTF_8)
                .build();

        Exception result = decoder.decode("Client#getItem", response);

        assertThat(result).isInstanceOf(BusinessException.class);
        BusinessException e = (BusinessException) result;
        assertThat(e.getErrorCode().getErrorCode()).isEqualTo("ITEM_0001");
        assertThat(e.getErrorCode().getMessage()).isEqualTo("아이템 없음");
        assertThat(e.getErrorCode().getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("파싱 안 되는 body면 FEIGN_CALL_FAILED로 대체한다")
    void fallsBackOnUnparsableBody() {
        Response response = Response.builder()
                .status(502)
                .request(Request.create(Request.HttpMethod.GET, "/items/1", Map.of(), new byte[0], StandardCharsets.UTF_8))
                .body("<html>Bad Gateway</html>", StandardCharsets.UTF_8)
                .build();

        Exception result = decoder.decode("Client#getItem", response);

        assertThat(result).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) result).getErrorCode()).isEqualTo(ErrorResponseCode.FEIGN_CALL_FAILED);
    }

    @Test
    @DisplayName("표준 HttpStatus에 없는 상태코드면 FEIGN_CALL_FAILED로 대체한다")
    void fallsBackOnNonStandardStatusCode() {
        String body = "{\"success\":false,\"errorCode\":\"ITEM_0001\",\"message\":\"아이템 없음\"}";
        Response response = Response.builder()
                .status(499) // nginx 확장 코드 - HttpStatus enum에 없음
                .request(Request.create(Request.HttpMethod.GET, "/items/1", Map.of(), new byte[0], StandardCharsets.UTF_8))
                .body(body, StandardCharsets.UTF_8)
                .build();

        Exception result = decoder.decode("Client#getItem", response);

        assertThat(result).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) result).getErrorCode()).isEqualTo(ErrorResponseCode.FEIGN_CALL_FAILED);
    }
}
