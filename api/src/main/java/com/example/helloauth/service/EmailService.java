package com.example.helloauth.service;

import java.time.Duration;

/**
 * Outbound email.
 *
 * <p>Real delivery is out of scope per the PRD, but the interface is the one a real implementation
 * would satisfy, so swapping in SMTP needs no change at any call site.
 */
public interface EmailService {

    /**
     * @param email the registered address to send to
     * @param plaintextToken the reset token. The only place the plaintext exists outside this call
     *     is the message itself — the database holds a hash.
     * @param validFor how long the token stays redeemable, so the message can say so
     */
    void sendPasswordResetEmail(String email, String plaintextToken, Duration validFor);
}
