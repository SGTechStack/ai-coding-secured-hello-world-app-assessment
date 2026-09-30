package com.assessment.auth.password;

/**
 * Where a freshly issued reset link is delivered outside the logging pipeline.
 *
 * <p>This interface exists to keep the reset URL <strong>out of Logback entirely</strong>. Nothing
 * routed through SLF4J means no appender, no ECS document, no audit file, and no chance of the
 * token reaching a log aggregator (ticket 11). {@code EmailService}'s structured event carries
 * neither the token nor the link; this channel is the only thing that ever sees them.
 *
 * <p>The default implementation discards. Only the {@code dev} profile supplies one that renders,
 * and it is absent from the context entirely in every other profile.
 */
public interface ResetLinkChannel {

  void deliver(String email, String token);

  /** The production behaviour: the link is not rendered anywhere. */
  static ResetLinkChannel discarding() {
    return (email, token) -> {};
  }
}
