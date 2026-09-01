package com.other.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalTestController {

    @GetMapping("/internal/test")
    public String internal() {
        return "ok";
    }
}
