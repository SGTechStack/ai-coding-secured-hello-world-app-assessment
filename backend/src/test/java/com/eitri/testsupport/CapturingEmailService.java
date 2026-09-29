package com.eitri.testsupport;

import com.eitri.passwordreset.EmailService;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Records reset emails instead of logging them, so tests can read the plaintext token. */
public final class CapturingEmailService implements EmailService {

    public record SentEmail(String email, String link) {

        public String token() {
            String query = URI.create(link).getQuery();
            if (query == null || !query.startsWith("token=")) {
                throw new AssertionError("Reset link has no token: " + link);
            }
            return query.substring("token=".length());
        }
    }

    private final List<SentEmail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void sendPasswordResetEmail(String email, String resetLink) {
        sent.add(new SentEmail(email, resetLink));
    }

    public List<SentEmail> sent() {
        return List.copyOf(sent);
    }

    public SentEmail last() {
        if (sent.isEmpty()) {
            throw new AssertionError("No reset email was sent");
        }
        return sent.getLast();
    }

    public void clear() {
        sent.clear();
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        CapturingEmailService capturingEmailService() {
            return new CapturingEmailService();
        }
    }
}
