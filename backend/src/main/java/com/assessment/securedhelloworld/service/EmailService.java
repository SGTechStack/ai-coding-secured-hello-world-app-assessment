package com.assessment.securedhelloworld.service;

/**
 * PRD-scoped as a stub: no real SMTP is part of this build (PRD "Out of Scope"). The interface
 * exists so a real implementation can be swapped in later without touching {@link PasswordResetService}.
 */
public interface EmailService {

    void sendPasswordResetEmail(String email, String resetLink);
}
