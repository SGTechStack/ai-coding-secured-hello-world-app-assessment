package com.example.hello.passwordreset;

/** Outbound mail boundary. Real SMTP delivery is out of scope for this build. */
public interface EmailService {

  void sendPasswordResetEmail(String toEmail, String resetLink);
}
