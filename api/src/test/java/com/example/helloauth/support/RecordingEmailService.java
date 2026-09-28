package com.example.helloauth.support;

import com.example.helloauth.service.EmailService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Captures the reset tokens the application would have emailed.
 *
 * <p>This is the whole reason {@code EmailService} is an interface. A reset token exists in exactly
 * two forms — a hash in the database and plaintext in the message — so without intercepting the
 * message there is no way for a test to redeem a token, and the single-use and expiry requirements
 * could not be verified at the HTTP boundary at all.
 */
public class RecordingEmailService implements EmailService {

    public record SentEmail(String recipient, String token, Duration validFor) {}

    private final List<SentEmail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void sendPasswordResetEmail(String email, String plaintextToken, Duration validFor) {
        sent.add(new SentEmail(email, plaintextToken, validFor));
    }

    public List<SentEmail> sent() {
        return List.copyOf(sent);
    }

    public Optional<SentEmail> lastSent() {
        return sent.isEmpty() ? Optional.empty() : Optional.of(sent.get(sent.size() - 1));
    }

    /**
     * @return the token from the most recent email, failing the test if nothing was sent — a silent
     *     empty here would otherwise turn "no email was sent" into a confusing null downstream
     */
    public String lastToken() {
        return lastSent()
                .orElseThrow(() -> new AssertionError("No password reset email was sent"))
                .token();
    }

    public void clear() {
        sent.clear();
    }
}
