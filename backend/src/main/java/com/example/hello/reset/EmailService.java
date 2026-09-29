package com.example.hello.reset;

public interface EmailService {
  void sendPasswordResetEmail(String email, String resetLink);
}
