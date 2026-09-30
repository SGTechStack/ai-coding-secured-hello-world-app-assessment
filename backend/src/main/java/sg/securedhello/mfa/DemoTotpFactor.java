package sg.securedhello.mfa;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dev-only demo administrator's factor, with a secret committed to git. It exists under {@code dev} only, so no
 * other profile can bind a known secret. The factor is stored exactly as a real enrolment stores it: the secret sealed
 * with its context prefix under the current key (ADR-028), and bound through {@link TotpUserDetails#boundFrom}, as
 * confirmation binds a pending enrolment (ADR-053), with no code used yet.
 */
@Component
@Profile("dev")
public class DemoTotpFactor {

    private final TotpUserDetailsRepository factors;
    private final TotpSecretCipher cipher;
    private final Clock clock;

    DemoTotpFactor(TotpUserDetailsRepository factors, TotpSecretCipher cipher, Clock clock) {
        this.factors = factors;
        this.cipher = cipher;
        this.clock = clock;
    }

    /** Binds {@code secret} as the factor of {@code userId}, an account with none, in the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enrol(UUID userId, byte[] secret) {
        if (factors.existsById(userId)) {
            throw new IllegalStateException("The account already holds a factor");
        }
        Instant now = clock.instant();
        PendingTotp sealed = new PendingTotp(userId, cipher.seal(userId, secret), cipher.keyVersion(), now);
        factors.save(TotpUserDetails.boundFrom(sealed, TotpWindow.NEVER_USED, now));
    }

    /** Whether {@code userId}'s factor holds {@code secret}; false with no factor, or once it has been re-enrolled. */
    @Transactional(readOnly = true)
    public boolean holds(UUID userId, byte[] secret) {
        return factors.findById(userId)
                .map(factor -> MessageDigest.isEqual(secret,
                        cipher.open(userId, factor.getKeyVersion(), factor.getTotpKey())))
                .orElse(false);
    }
}
