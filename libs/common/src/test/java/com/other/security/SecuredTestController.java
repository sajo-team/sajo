package com.other.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecuredTestController {

    @GetMapping("/secured")
    @PreAuthorize("hasRole('MASTER')")
    public String secured() {
        return "ok";
    }
}
