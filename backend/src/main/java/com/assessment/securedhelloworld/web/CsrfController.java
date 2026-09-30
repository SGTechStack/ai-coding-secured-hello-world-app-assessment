package com.assessment.securedhelloworld.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * No-op GET the frontend calls once on load to make {@code CsrfCookieFilter} render the CSRF
 * token, so the XSRF-TOKEN cookie exists before the first state-changing request.
 */
@RestController
@RequestMapping("/api")
public class CsrfController {

    @GetMapping("/csrf")
    public ResponseEntity<Void> primeCsrfCookie() {
        return ResponseEntity.noContent().build();
    }
}
