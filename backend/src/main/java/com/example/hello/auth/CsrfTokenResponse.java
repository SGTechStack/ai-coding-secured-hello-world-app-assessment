package com.example.hello.auth;

/** The header the SPA must send on state-changing requests, and its current value. */
public record CsrfTokenResponse(String headerName, String token) {}
