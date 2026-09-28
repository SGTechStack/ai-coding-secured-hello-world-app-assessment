package com.assessment.securedhelloworld.hello;

import com.assessment.securedhelloworld.auth.AppUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The app's namesake authenticated greeting endpoint
 * ({@code GET /api/hello}). Also doubles as the frontend's "who am I"
 * check on load, so the response includes the caller's username and
 * role — the role lets the frontend gate role-restricted UI (e.g. the
 * "Manage users" entry point) instead of relying solely on a reactive
 * 403 after the user clicks through (IM8 ac-1).
 */
@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public Map<String, String> hello(@AuthenticationPrincipal AppUserDetails principal) {
        return Map.of(
                "message", "Hello, " + principal.getUsername(),
                "username", principal.getUsername(),
                "role", principal.getUser().getRole().name());
    }
}
