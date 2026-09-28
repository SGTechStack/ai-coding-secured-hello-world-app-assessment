package com.example.helloworldauth.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Unauthenticated smoke endpoint proving the wiring end to end. */
@RestController
public class PingController {

    @GetMapping("/api/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }
}
