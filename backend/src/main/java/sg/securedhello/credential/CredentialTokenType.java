package sg.securedhello.credential;

import java.time.Duration;

/**
 * The kinds of credential token; the database admits exactly these ({@code ck_credential_tokens_type}, ADR-007). The
 * name is also the type label hashed in front of the token, so a token of one type never redeems as another.
 */
public enum CredentialTokenType {

    /** Sets a pending registration's first password and activates it: 24 hours (ADR-007; ASVS 6.4.1). */
    ACTIVATION(Duration.ofHours(24)),

    /** Sets a new password on an activated account: 30 minutes (ADR-007). */
    PASSWORD_RESET(Duration.ofMinutes(30));

    private final Duration lifetime;

    CredentialTokenType(Duration lifetime) {
        this.lifetime = lifetime;
    }

    /** How long a token of this type stays redeemable after it is minted. */
    public Duration lifetime() {
        return lifetime;
    }
}
