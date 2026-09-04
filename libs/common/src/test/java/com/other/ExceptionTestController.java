package com.other;

import com.sajo.common.code.ErrorResponseCode;
import com.sajo.common.exception.BusinessException;
import com.sajo.common.feign.FeignApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class ExceptionTestController {

    @PostMapping("/validate-body")
    public String validateBody(@Valid @RequestBody ValidationRequest request) {
        return "ok";
    }

    @GetMapping("/validate-param")
    public String validateParam(@RequestParam @Min(1) int page) {
        return "ok";
    }

    @GetMapping("/require-header")
    public String requireHeader(@RequestHeader("X-User-Id") String userId) {
        return "ok";
    }

    @GetMapping("/business-error")
    public String businessError() {
        throw new BusinessException(ErrorResponseCode.NOT_FOUND, "hub id 5를 찾을 수 없습니다");
    }

    @GetMapping("/boom")
    public String boom() {
        throw new RuntimeException("boom");
    }

    @GetMapping("/feign-error")
    public String feignError() {
        throw new FeignApiException("ACCOUNT_0001", "계좌를 찾을 수 없습니다", 404);
    }

    @GetMapping("/feign-error-non-standard-status")
    public String feignErrorNonStandardStatus() {
        throw new FeignApiException("CLIENT_CLOSED", "클라이언트가 연결을 종료했습니다", 499);
    }
}
