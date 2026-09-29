package com.example.helloworldauth.web;

import com.example.helloworldauth.user.User;
import com.example.helloworldauth.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

/**
 * Current-user endpoint. Returns the authenticated account's username and role
 * so the SPA can restore its view (and show admin navigation) after a page
 * reload — the greeting endpoint only carries the username. 401 when
 * unauthenticated (handled by the security entry point before this runs).
 *
 * <p>Role is looked up from the users table rather than trusted from client
 * state; authorization everywhere else remains server-enforced.
 */
@RestController
public class MeController {

    private final UserRepository users;

    public MeController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/api/me")
    public ResponseEntity<Map<String, String>> me(Principal principal) {
        return users.findByUsername(principal.getName())
            .map(user -> ResponseEntity.ok(Map.of(
                "username", user.getUsername(),
                "role", user.getRole().name())))
            .orElseGet(() -> ResponseEntity.status(401).build());
    }
}
