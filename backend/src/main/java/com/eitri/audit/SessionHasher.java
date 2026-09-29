package com.eitri.audit;

import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Produces a non-reversible, deployment-keyed correlation value for a server-side session. */
@Component
public final class SessionHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public SessionHasher(@Value("${app.security.session-hash-key}") String key) {
        if (key.isBlank()) {
            throw new IllegalArgumentException("app.security.session-hash-key must not be blank");
        }
        this.key = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    Optional<String> hash(HttpSession session) {
        return session == null ? Optional.empty() : Optional.of(hash(session.getId()));
    }

    String hash(String sessionId) {
        try {
            Mac hmac = Mac.getInstance(ALGORITHM);
            hmac.init(key);
            return HexFormat.of().formatHex(hmac.doFinal(sessionId.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }
}
