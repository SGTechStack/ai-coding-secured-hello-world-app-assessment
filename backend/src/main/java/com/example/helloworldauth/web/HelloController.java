package com.example.helloworldauth.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

/**
 * Protected greeting. Returns the personalized message for an authenticated
 * session; Spring Security rejects an unauthenticated request with 401 before
 * this handler is reached (see SecurityConfig: anyRequest().authenticated()).
 *
 * <p>Uses {@link Principal#getName()} rather than {@code @AuthenticationPrincipal
 * UserDetails}: the login flow stores a String principal (the username), so a
 * UserDetails-typed argument would bind to null. getName() is the username for
 * every principal type.
 */
@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public Map<String, String> hello(Principal principal) {
        return Map.of("message", "Hello, " + principal.getName());
    }
}
