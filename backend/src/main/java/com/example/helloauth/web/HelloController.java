package com.example.helloauth.web;

import com.example.helloauth.security.AppUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HelloController {

    /**
     * Personalized greeting for authenticated users. Unauthenticated requests are rejected with
     * 401 by the security filter chain before reaching here (Story 5).
     */
    @GetMapping("/hello")
    public Dtos.MessageResponse hello(@AuthenticationPrincipal AppUserDetails principal) {
        return new Dtos.MessageResponse("Hello, " + principal.getUsername());
    }
}
