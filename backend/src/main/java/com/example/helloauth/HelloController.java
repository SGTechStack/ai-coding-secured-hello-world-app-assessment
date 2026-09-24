package com.example.helloauth;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The protected hello endpoint. Anonymous callers get 401 from the filter
 * chain before reaching this; authenticated users get a personalized
 * greeting ("Hello, <username>").
 */
@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public Map<String, String> hello(Authentication authentication) {
        return Map.of("message", "Hello, " + authentication.getName());
    }
}
