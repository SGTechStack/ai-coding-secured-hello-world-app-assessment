package com.example.demo_app.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * The key-value pairs {@link AuditLog} hands to SLF4J, which ECS JSON logging turns into top-level
 * fields. {@code AuditLogApiTest} covers when each event is logged, at the HTTP seam.
 */
class AuditLogTest {

  private final AuditLog auditLog = new AuditLog();
  private final Logger logger = (Logger) LoggerFactory.getLogger("AUDIT");
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private final MockHttpServletRequest request = new MockHttpServletRequest();

  @BeforeEach
  void attachAppender() {
    appender.start();
    logger.addAppender(appender);
    request.setRemoteAddr("203.0.113.7");
  }

  @AfterEach
  void detachAppender() {
    logger.detachAppender(appender);
  }

  @Test
  void logsTheStandardFieldsAsKeyValuePairsAtInfo() {
    auditLog.record(AuditEvent.LOGIN_FAILURE, "johndoe", request);

    ILoggingEvent line = appender.list.getFirst();
    assertThat(line.getLevel()).hasToString("INFO");
    assertThat(line.getKeyValuePairs())
        .extracting(pair -> pair.key, pair -> pair.value)
        .containsExactly(
            tuple("event", "LOGIN_FAILURE"),
            tuple("actor", "johndoe"),
            tuple("ip", "203.0.113.7"),
            tuple("outcome", "failure"));
    assertThat(line.getFormattedMessage())
        .isEqualTo("event=LOGIN_FAILURE actor=johndoe ip=203.0.113.7 outcome=failure");
  }

  @Test
  void aMissingActorIsAnonymous() {
    auditLog.record(AuditEvent.LOGOUT, null, request);

    assertThat(pairs()).containsEntry("actor", "anonymous").containsEntry("outcome", "success");
  }

  @Test
  void eventSpecificFieldsFollowTheStandardOnesAndAreNeutralised() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("target", "bob\nevent=FORGED");
    fields.put("newRole", null);

    auditLog.record(AuditEvent.USER_ROLE_CHANGED, "admin\u0085", request, fields);

    assertThat(pairs())
        .containsExactly(
            Map.entry("event", "USER_ROLE_CHANGED"),
            Map.entry("actor", "admin_"),
            Map.entry("ip", "203.0.113.7"),
            Map.entry("outcome", "success"),
            Map.entry("target", "bob_event=FORGED"),
            Map.entry("newRole", "null"));
  }

  @Test
  void placeholdersInValuesAreNotInterpreted() {
    auditLog.record(AuditEvent.LOGIN_FAILURE, "{}", request);

    assertThat(appender.list.getFirst().getFormattedMessage()).contains("actor={} ");
  }

  private Map<String, Object> pairs() {
    Map<String, Object> pairs = new LinkedHashMap<>();
    for (KeyValuePair pair : appender.list.getFirst().getKeyValuePairs()) {
      pairs.put(pair.key, pair.value);
    }
    return pairs;
  }
}
