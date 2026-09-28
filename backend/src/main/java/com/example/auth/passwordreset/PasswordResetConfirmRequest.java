package com.example.auth.passwordreset;

public record PasswordResetConfirmRequest(String token, String newPassword) {}
