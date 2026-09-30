package com.example.auth.passwordreset;

/** Published inside the reset-request transaction; the email goes out only once it commits. */
record PasswordResetEmailRequested(String toEmail, String resetLink) {}
