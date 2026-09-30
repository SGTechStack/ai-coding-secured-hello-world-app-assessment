package com.example.auth.auth;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Story 5: a minimal authenticated-only smoke-test endpoint. */
@RestController
public class HelloController {

    @GetMapping(value = "/api/hello", produces = MediaType.TEXT_PLAIN_VALUE)
    public String hello(Authentication authentication) {
        return "Hello, " + authentication.getName();
    }
}
