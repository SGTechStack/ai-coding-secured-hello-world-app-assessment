package com.example.hello.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AuditLog {
  private static final Logger LOG = LoggerFactory.getLogger("security.audit");

  public void record(String event, String actor, String target, String outcome) {
    LOG.atInfo()
        .addKeyValue("event", event)
        .addKeyValue("actor", safe(actor))
        .addKeyValue("target", safe(target))
        .addKeyValue("outcome", outcome)
        .log("security_event");
  }

  private String safe(String value) {
    if (value == null) return "anonymous";
    String sanitized = value.replaceAll("[^a-zA-Z0-9@._:-]", "_");
    return sanitized.substring(0, Math.min(sanitized.length(), 254));
  }
}
