package com.sajo.common.feign;

import com.sajo.common.code.ErrorResponseCode;
import com.sajo.common.exception.BusinessException;
import com.sajo.common.response.ErrorResponse;
import feign.Response;
import feign.codec.ErrorDecoder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

public class CommonFeignErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;
    private final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

    public CommonFeignErrorDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.body() == null) {
            return defaultDecoder.decode(methodKey, response);
        }

        try {
            ErrorResponse errorResponse = objectMapper.readValue(response.body().asInputStream(), ErrorResponse.class);

            return new FeignApiException(errorResponse.errorCode(), errorResponse.message(), response.status());
        } catch (IOException | JacksonException e) {
            // 상대 서비스가 우리 ErrorResponse 포맷이 아닌 응답을 준 경우
            return new BusinessException(ErrorResponseCode.FEIGN_CALL_FAILED);
        }
    }
}
