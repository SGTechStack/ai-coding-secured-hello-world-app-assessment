package com.example.auth.web;

import java.util.Map;

import com.example.auth.user.UserPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/api/hello")
    Map<String, String> hello(@AuthenticationPrincipal UserPrincipal principal) {
        return Map.of("message", "Hello, " + principal.getUsername());
    }
}
