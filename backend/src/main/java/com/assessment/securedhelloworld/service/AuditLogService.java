package com.assessment.securedhelloworld.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Structured, ECS-flavoured ("event", "actor", "target", "outcome") audit logging (IM8 lm-4,
 * lm-15). This is the ONLY place audit events are written, so redaction (lm-19) is enforced in
 * one seam rather than trusted to every call site: {@link #event} never accepts a raw password or
 * reset token, only the caller-supplied key/value context, and known-sensitive keys are redacted
 * defensively even if a caller slips one in.
 */
@Service
public class AuditLogService {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    private static final Set<String> SENSITIVE_KEYS = Set.of("password", "token", "passwordhash", "secret");

    public void event(String eventName, String outcome, String actor, String target, Map<String, Object> context) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("event", eventName);
        fields.put("outcome", outcome);
        fields.put("actor", actor == null ? "anonymous" : actor);
        if (target != null) {
            fields.put("target", target);
        }
        if (context != null) {
            context.forEach((key, value) -> {
                if (SENSITIVE_KEYS.contains(key.toLowerCase())) {
                    fields.put(key, "[REDACTED]");
                } else {
                    fields.put(key, value);
                }
            });
        }
        try {
            fields.forEach((k, v) -> MDC.put(k, String.valueOf(v)));
            AUDIT.info(eventName);
        } finally {
            fields.keySet().forEach(MDC::remove);
        }
    }

    public void event(String eventName, String outcome, String actor, String target) {
        event(eventName, outcome, actor, target, Map.of());
    }
}
