package com.example.securedhello.controller;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated health endpoint. Lets the frontend prove backend
 * connectivity cross-origin. Reads the current time through the injected
 * {@link Clock} so the shared time source is exercised from the first slice.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final Clock clock;

    public HealthController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        Instant now = clock.instant();
        return Map.of(
                "status", "UP",
                "time", now.toString());
    }
}
