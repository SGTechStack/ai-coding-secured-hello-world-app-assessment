package com.assessment.securedhelloworld.web;

import com.assessment.securedhelloworld.web.dto.HelloResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.assessment.securedhelloworld.security.AppUserDetails;

/**
 * {@code /api/hello} is `authenticated()` in SecurityConfig, so an anonymous or expired-session
 * request never reaches this method at all — it is turned into 401 by JsonAuthEntryPoint before
 * Spring MVC dispatches (PRD Story 5).
 */
@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public HelloResponse hello(@AuthenticationPrincipal AppUserDetails principal) {
        return new HelloResponse("Hello, " + principal.getUsername());
    }
}
