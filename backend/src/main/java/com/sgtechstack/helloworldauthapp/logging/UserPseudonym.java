package com.sgtechstack.helloworldauthapp.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Turns a username into a stable, non-reversible reference for use in logs.
 *
 * <h2>Why</h2>
 *
 * The audit lines this application writes are genuinely necessary — "who
 * locked this account and when" is a question an operator has to be able to
 * answer. But they were written with the raw username as the subject, and the
 * {@code users} table ties every username to an email address. That makes the
 * log a second, unbounded copy of a personal-data linkage: read it and you
 * learn when an identified individual logged in, how often they failed, and
 * what times of day they work. Logs are also the least-governed data store in
 * most deployments — no retention, broad read access, shipped to aggregators
 * nobody inventoried.
 *
 * <h2>Why a keyed hash and not simply dropping the username</h2>
 *
 * Dropping the identifier entirely would be the strongest privacy answer and
 * the wrong security one: "five failed logins" is not actionable without
 * knowing whether it was five against one account or one against five. A keyed
 * pseudonym keeps exactly the property the investigation needs — two lines
 * about the same account share a reference — while removing the property
 * privacy objects to: the reference does not name anybody, and cannot be
 * reversed without the key.
 *
 * <p>Keyed (HMAC) rather than a plain digest, because the input space is small
 * and guessable. A bare {@code SHA-256("alice")} is trivially reversed by
 * hashing a wordlist, which would make the pseudonym decorative. The key is
 * what makes the mapping one-way in practice.
 *
 * <h2>The unkeyed default</h2>
 *
 * When {@code app.logging.pseudonym-salt} is unset, a random key is generated
 * for the lifetime of the process. That is a deliberate default, not an
 * oversight: shipping a fixed fallback key in a public repository would make
 * every pseudonym in every deployment reversible by anyone who can read this
 * file, which is strictly worse than logging the username plainly, because it
 * would look protected and not be.
 *
 * <p>The cost of the per-boot key is that references do not survive a restart,
 * so an investigation spanning one cannot join across it. Deployments that need
 * that must supply a key from a secret manager. Stated here rather than left to
 * be discovered from a confusing investigation.
 */
@Component
public class UserPseudonym {

    private static final Logger log = LoggerFactory.getLogger(UserPseudonym.class);

    private static final String ALGORITHM = "HmacSHA256";
    private static final String PREFIX = "u_";

    /**
     * 12 hex characters — 48 bits. Collisions become likely somewhere around
     * 16 million distinct usernames, which is far past this application's
     * scale, and a short reference is one an operator can actually read and
     * compare across lines.
     */
    private static final int REFERENCE_HEX_LENGTH = 12;

    static final String UNKNOWN = "u_unknown";

    private final SecretKeySpec key;

    public UserPseudonym(@Value("${app.logging.pseudonym-salt:}") String configuredSalt) {
        boolean generated = configuredSalt == null || configuredSalt.isBlank();
        byte[] keyBytes = generated ? randomKey() : configuredSalt.getBytes(StandardCharsets.UTF_8);
        this.key = new SecretKeySpec(keyBytes, ALGORITHM);

        if (generated) {
            log.info("No app.logging.pseudonym-salt configured, so log user references are keyed per boot. "
                    + "They stay consistent within this run but cannot be correlated across a restart; set the "
                    + "property from a secret manager if cross-restart correlation is needed.");
        }
    }

    /**
     * The reference for {@code username}, or {@code u_unknown} when there is
     * no username to reference (an unauthenticated request, or a login attempt
     * that submitted none).
     *
     * <p>Lower-cased first, because the application looks accounts up
     * case-insensitively. Without that, {@code Alice} and {@code alice} would
     * produce different references for one account and split its audit trail
     * in two.
     */
    public String of(String username) {
        if (username == null || username.isBlank()) {
            return UNKNOWN;
        }

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal(username.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return PREFIX + HexFormat.of().formatHex(digest).substring(0, REFERENCE_HEX_LENGTH);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(ALGORITHM + " is not available", e);
        }
    }

    private static byte[] randomKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}
