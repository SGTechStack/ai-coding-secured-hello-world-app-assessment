package com.example.helloauth.web;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The protected greeting — the thing that proves authentication actually worked.
 *
 * <p>Plain text, not JSON: the PRD writes the response <em>as</em> {@code "Hello, <username>"}, and
 * wrapping it in an envelope would be a different response than the one specified.
 *
 * <p>There is no "if not authenticated" branch. Reaching this method at all means the filter chain
 * already required a session, so the 401 in story 5 is produced there rather than here. A null-check
 * would be dead code that implied otherwise.
 */
@RestController
public class HelloController {

    @GetMapping(path = "/api/hello", produces = MediaType.TEXT_PLAIN_VALUE)
    public String hello(Authentication authentication) {
        return "Hello, " + authentication.getName();
    }
}
