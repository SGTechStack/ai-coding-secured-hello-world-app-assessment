package com.example.auth.passwordreset;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * Generates and hashes password-reset tokens.
 *
 * Tokens are 256 bits (32 bytes) from SecureRandom, Base64URL-encoded without
 * padding — infeasible to guess (Story 46). Only the SHA-256 hex digest is
 * persisted; the plaintext is returned once for emailing and never stored.
 *
 * SHA-256 (deterministic, unsalted) is used rather than BCrypt because the
 * confirm endpoint must look the token up by hash — a per-hash salt would make
 * lookup impossible. This is safe precisely because the token is high-entropy,
 * unlike a user-chosen password. See ADR-0002.
 */
@Component
public class ResetTokenService {

    private static final int TOKEN_BYTES = 32; // 256 bits

    private final SecureRandom secureRandom = new SecureRandom();

    /** Returns a fresh, unguessable plaintext token (Base64URL, no padding). */
    public String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Deterministic SHA-256 hex digest for storage and lookup. */
    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
