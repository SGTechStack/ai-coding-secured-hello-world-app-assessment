package com.eitri.greeting;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Greets the authenticated account by username. Access is granted to USER and ADMIN by the matrix. */
@RestController
class GreetingController {

    @GetMapping(value = "${app.api.base-path}/hello", produces = MediaType.TEXT_PLAIN_VALUE)
    String hello(Authentication authentication) {
        return "Hello, " + authentication.getName();
    }
}
