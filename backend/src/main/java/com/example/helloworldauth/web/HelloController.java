package com.example.helloworldauth.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Protected greeting. Returns the personalized message for an authenticated
 * session; Spring Security rejects an unauthenticated request with 401 before
 * this handler is reached (see SecurityConfig: anyRequest().authenticated()).
 */
@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public Map<String, String> hello(@AuthenticationPrincipal UserDetails principal) {
        return Map.of("message", "Hello, " + principal.getUsername());
    }
}
