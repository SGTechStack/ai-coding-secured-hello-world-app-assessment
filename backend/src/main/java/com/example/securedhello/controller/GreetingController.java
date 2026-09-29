package com.example.securedhello.controller;

import java.security.Principal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Protected greeting endpoint. Returns a personalized greeting for the
 * authenticated User; unauthenticated requests never reach this handler
 * because the security filter chain returns 401 first.
 */
@RestController
@RequestMapping("/api")
public class GreetingController {

    @GetMapping("/hello")
    public String hello(Principal principal) {
        return "Hello, " + principal.getName();
    }
}
