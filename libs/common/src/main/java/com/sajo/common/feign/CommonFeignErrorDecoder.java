package com.sajo.common.feign;

import com.sajo.common.code.ErrorResponseCode;
import com.sajo.common.exception.BusinessException;
import com.sajo.common.response.ErrorResponse;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.http.HttpStatus;
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

            FeignErrorCode errorCode = new FeignErrorCode(
                    HttpStatus.valueOf(response.status()), errorResponse.errorCode(), errorResponse.message());

            return new BusinessException(errorCode);
        } catch (IOException | JacksonException | IllegalArgumentException e) {
            // 상대 서비스가 우리 ErrorResponse 포맷이 아닌 응답을 줬거나 (IOException | JacksonException)
            // 표준 HttpStatus에 없는 상태코드를 준 경우
            return new BusinessException(ErrorResponseCode.FEIGN_CALL_FAILED);
        }
    }
}
