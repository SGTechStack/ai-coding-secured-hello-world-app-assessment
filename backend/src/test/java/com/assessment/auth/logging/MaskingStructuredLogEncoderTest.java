package com.assessment.auth.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A <strong>named required gate</strong> (story 1.24).
 *
 * <p>It is required by name because {@link MaskingStructuredLogEncoder} extends an internal Spring
 * Boot class and is the only mechanism satisfying the boundary-masking constraint. If a Boot
 * upgrade changes {@code StructuredLogEncoder}, the failure must surface here rather than as
 * silently unmasked log output.
 */
class MaskingStructuredLogEncoderTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private LoggerContext context;
  private MaskingStructuredLogEncoder encoder;

  @BeforeEach
  void setUp() {
    context = new LoggerContext();
    // StructuredLogEncoder.createFormatter() reads the Environment out of the logger context under
    // Environment.class.getName(); at runtime SpringBootJoranConfigurator puts it there. Verified
    // against the bytecode of spring-boot-4.0.7.
    context.putObject(Environment.class.getName(), new MockEnvironment());
    // A standalone LoggerContext has no MDC adapter; the ECS formatter reads one unconditionally.
    context.setMDCAdapter(new LogbackMDCAdapter());
    context.start();
    encoder = new MaskingStructuredLogEncoder();
    encoder.setContext(context);
    encoder.setFormat("ecs");
    encoder.start();
  }

  @AfterEach
  void tearDown() {
    encoder.stop();
    context.stop();
  }

  private JsonNode encode(KeyValuePair... pairs) {
    Logger logger = context.getLogger("AUDIT");
    // Built through this constructor rather than the no-arg one so the event carries its
    // LoggerContext: the ECS formatter reads the MDC adapter off it.
    LoggingEvent event = new LoggingEvent(Logger.FQCN, logger, Level.INFO, "EVENT", null, null);
    for (KeyValuePair pair : pairs) {
      event.addKeyValuePair(pair);
    }
    byte[] encoded = encoder.encode((ILoggingEvent) event);
    return MAPPER.readTree(new String(encoded, StandardCharsets.UTF_8).strip());
  }

  @Test
  @DisplayName("creates a nested error object when no throwable exists")
  void createsErrorObjectWithoutAThrowable() {
    // The deliberate deviation from Encoder:408. The recipe STRIPS these keys when nothing threw.
    // Lockout, access denial and forced-change denial all carry an error semantic with no
    // throwable behind them, so the recipe's behaviour would delete nine fields across the three
    // most important security events -- with no failing build (spec.md S11).
    JsonNode line =
        encode(
            new KeyValuePair("error.code", "423"),
            new KeyValuePair("error.category", "authentication"),
            new KeyValuePair("error.follow_up_action", "Contact an administrator."));

    assertThat(line.has("error.code")).as("flat key must be folded away").isFalse();
    JsonNode error = line.get("error");
    assertThat(error).isNotNull();
    assertThat(error.get("code").asString()).isEqualTo("423");
    assertThat(error.get("category").asString()).isEqualTo("authentication");
    assertThat(error.get("follow_up_action").asString()).isEqualTo("Contact an administrator.");
  }

  @Test
  @DisplayName("masks secret-bearing keys at the boundary")
  void masksSecrets() {
    // Nothing in this application deliberately logs any of these. The encoder is the last line of
    // defence, so that a future careless addKeyValue cannot leak one (Std_Logging:326).
    JsonNode line =
        encode(
            new KeyValuePair("password", "quiet harbour lantern"),
            new KeyValuePair("token", "abc123"),
            new KeyValuePair("username", "alice"),
            new KeyValuePair("email", "alice@example.com"),
            new KeyValuePair("user.name", "alice"),
            new KeyValuePair("event.reason", "LOGIN_SUCCESS"));

    assertThat(line.get("password").asString()).isEqualTo("***MASKED***");
    assertThat(line.get("token").asString()).isEqualTo("***MASKED***");
    assertThat(line.get("username").asString()).isEqualTo("***MASKED***");
    assertThat(line.get("email").asString()).isEqualTo("***MASKED***");
    // Folded by the ECS formatter into user:{name:...}; masking must follow it there. This
    // application never logs user.name in the first place -- this is the boundary guarantee.
    assertThat(line.get("user").get("name").asString()).isEqualTo("***MASKED***");
    assertThat(line.get("event").get("reason").asString())
        .as("non-secret fields must survive untouched")
        .isEqualTo("LOGIN_SUCCESS");
  }

  @Test
  @DisplayName("leaves an ordinary line structurally intact")
  void leavesOrdinaryLinesAlone() {
    JsonNode line = encode(new KeyValuePair("event.action", "AUTHENTICATION"));
    assertThat(line.get("event").get("action").asString()).isEqualTo("AUTHENTICATION");
    assertThat(line.has("error")).as("no error object is invented when there are no error keys").isFalse();
    assertThat(line.get("message").asString()).isEqualTo("EVENT");
  }
}
