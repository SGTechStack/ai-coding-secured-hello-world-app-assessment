package com.assessment.securedhelloworld.hello;

import com.assessment.securedhelloworld.auth.AppUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public String hello(@AuthenticationPrincipal AppUserDetails principal) {
        return "Hello, " + principal.getUsername();
    }
}
