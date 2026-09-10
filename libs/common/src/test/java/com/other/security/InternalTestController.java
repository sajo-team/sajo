package com.other.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalTestController {

    // 실제 서비스들의 internal 컨트롤러는 전부 /internal/v1/** 규칙을 따른다
    // (AccountInternalController, TradingInternalController 등 참고)
    @GetMapping("/internal/v1/test")
    public String internal() {
        return "ok";
    }
}
