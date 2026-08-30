package com.other;

import com.sajo.common.code.ErrorResponseCode;
import com.sajo.common.exception.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/business-error")
    public String businessError() {
        throw new BusinessException(ErrorResponseCode.NOT_FOUND, "hub id 5를 찾을 수 없습니다");
    }

    @GetMapping("/boom")
    public String boom() {
        throw new RuntimeException("boom");
    }
}
