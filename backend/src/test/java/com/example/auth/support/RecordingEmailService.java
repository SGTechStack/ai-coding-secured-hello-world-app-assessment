package com.example.auth.support;

import java.util.ArrayList;
import java.util.List;

import com.example.auth.passwordreset.EmailService;

/**
 * Test double for EmailService: records every reset link in memory so tests can
 * extract the plaintext token (which production stores only as a hash). This is
 * the "EmailService seam" from the spec's Testing Decisions.
 */
public class RecordingEmailService implements EmailService {

    private final List<String> sentLinks = new ArrayList<>();
    private String lastEmail;

    @Override
    public synchronized void sendPasswordResetEmail(String email, String resetLink) {
        this.lastEmail = email;
        this.sentLinks.add(resetLink);
    }

    public synchronized String lastResetLink() {
        return sentLinks.isEmpty() ? null : sentLinks.get(sentLinks.size() - 1);
    }

    /** Extracts the plaintext token query param from the last reset link. */
    public synchronized String lastToken() {
        String link = lastResetLink();
        if (link == null) {
            return null;
        }
        int idx = link.indexOf("token=");
        return idx < 0 ? null : link.substring(idx + "token=".length());
    }

    public synchronized String lastEmail() {
        return lastEmail;
    }

    public synchronized int sentCount() {
        return sentLinks.size();
    }

    public synchronized void reset() {
        sentLinks.clear();
        lastEmail = null;
    }
}
