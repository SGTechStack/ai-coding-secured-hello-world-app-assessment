package com.example.authapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Structured audit lines. Callers must never pass passwords or tokens. */
@Component
public class AuditLogger {

    private static final Logger LOG = LoggerFactory.getLogger("AUDIT");

    /** Logs {@code event key=value ...}; kv is alternating key, value. */
    public void log(String event, Object... kv) {
        StringBuilder sb = new StringBuilder("event=").append(event);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            sb.append(' ').append(kv[i]).append('=').append(sanitize(String.valueOf(kv[i + 1])));
        }
        LOG.info(sb.toString());
    }

    /** Strip control chars and whitespace (log forging) and cap the length of user-controlled values. */
    static String sanitize(String value) {
        String cleaned = value.replaceAll("[\\p{Cntrl}\\s]+", "_");
        return cleaned.length() > 100 ? cleaned.substring(0, 100) : cleaned;
    }
}
