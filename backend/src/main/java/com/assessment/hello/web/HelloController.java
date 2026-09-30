package com.assessment.hello.web;

import com.assessment.hello.dto.MessageResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HelloController {

    @GetMapping("/hello")
    public MessageResponse hello(Authentication authentication) {
        // Reaching here means the security filter chain already authenticated the
        // session; an unauthenticated request gets 401 before this method runs.
        return new MessageResponse("Hello, " + authentication.getName());
    }
}
