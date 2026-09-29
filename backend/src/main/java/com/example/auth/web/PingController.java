package com.example.auth.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Scaffold liveness probe; confirms the app boots and the filter chain permits it. */
@RestController
public class PingController {

    @GetMapping("/api/ping")
    Map<String, String> ping() {
        return Map.of("status", "ok");
    }
}
