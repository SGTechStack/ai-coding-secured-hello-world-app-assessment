package com.example.authapp.web;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping(path = "/api/hello", produces = MediaType.TEXT_PLAIN_VALUE)
    public String hello(Authentication auth) {
        return "Hello, " + auth.getName();
    }
}
