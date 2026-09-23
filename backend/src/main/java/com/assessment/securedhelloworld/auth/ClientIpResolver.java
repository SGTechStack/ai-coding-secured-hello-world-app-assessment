package com.assessment.securedhelloworld.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the client IP for IP-throttling purposes (Story 3). Reads
 * {@code X-Forwarded-For} first (first entry) since a real deployment
 * would sit behind a reverse proxy, falling back to the raw remote
 * address for local/dev use.
 */
final class ClientIpResolver {

    private ClientIpResolver() {
    }

    static String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
