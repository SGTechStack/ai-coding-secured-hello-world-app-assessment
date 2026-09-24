package com.example.demo_app.passwordreset;

/**
 * Sends the emails the app needs. Real delivery is out of scope: {@link LoggingEmailService} is
 * the only implementation.
 */
public interface EmailService {

  /** Sends {@code link}, which carries a live reset token, to {@code email}. */
  void sendPasswordResetEmail(String email, String link);
}
