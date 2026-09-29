package com.example.securedhello.service;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Emits structured Audit Events for security-relevant actions (login,
 * lockout, reset, admin mutations). Each line carries actor, outcome,
 * timestamp, and a correlationId — never credentials, token plaintext, or
 * password hashes. Time is read through the injected {@link Clock} so audit
 * timestamps are deterministic in tests.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");

    private final Clock clock;

    public AuditService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Records a security event.
     *
     * @param action  the event name, e.g. {@code LOGIN_SUCCESS}
     * @param actor   the acting principal (username) or {@code anonymous}
     * @param target  the affected principal, or {@code null} when not applicable
     * @param outcome {@code SUCCESS} or {@code FAILURE}
     */
    public void record(String action, String actor, String target, String outcome) {
        String correlationId = UUID.randomUUID().toString();
        log.info("audit action={} actor={} target={} outcome={} timestamp={} correlationId={}",
                action,
                actor == null ? "anonymous" : actor,
                target == null ? "-" : target,
                outcome,
                clock.instant(),
                correlationId);
    }
}
