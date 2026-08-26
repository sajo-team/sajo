package com.sajo.auth_service;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/api/v1/users/test")
    public String test() {
        return "auth-service is alive";
    }
}