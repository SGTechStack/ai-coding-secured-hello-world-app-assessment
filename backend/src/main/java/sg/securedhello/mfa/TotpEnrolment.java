package sg.securedhello.mfa;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriUtils;

import sg.securedhello.audit.AccountContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.user.UserAccountRepository;

/**
 * TOTP enrolment for an administrator with no factor (ADR-023; ADR-025): provisioning, then <em>enrolment
 * binding</em>. Both take the {@code users} row lock first, then touch the TOTP rows, the lock order everywhere
 * (spec, Sessions).
 *
 * <ul>
 *   <li><b>Provisioning</b> writes a <em>pending enrolment</em> only: a fresh 20-byte secret, sealed with its context
 *       prefix (ADR-028), replacing any earlier pending row. It resets no counter, on the account or on any factor.
 *       The secret leaves the server once, in the returned {@link Provisioning}, and is never stored in plaintext or
 *       logged.</li>
 *   <li><b>Confirmation</b> checks a code against the pending secret and copies the pending envelope verbatim into
 *       {@code totp_user_details}, which is the enrolment (ADR-053; REJ-071). A wrong code changes nothing and leaves
 *       the pending row for a retry (T-MFA-016).</li>
 * </ul>
 */
@Service
public class TotpEnrolment {

    private static final Base32 BASE32 = new Base32();

    private final UserAccountRepository accounts;
    private final PendingTotpRepository pending;
    private final TotpUserDetailsRepository factors;
    private final TotpSecretCipher cipher;
    private final TotpProperties properties;
    private final AuditEmitter audit;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final SecureRandom random = new SecureRandom();

    TotpEnrolment(UserAccountRepository accounts, PendingTotpRepository pending, TotpUserDetailsRepository factors,
            TotpSecretCipher cipher, TotpProperties properties, AuditEmitter audit, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.pending = pending;
        this.factors = factors;
        this.cipher = cipher;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * What provisioning returns, once: the {@code otpauth} URI, the same secret in Base32 for manual entry, and the
     * URI as a PNG QR code. The URI embeds the secret, so it is as sensitive as the Base32 string (ADR-025).
     */
    public record Provisioning(String otpauthUri, String secretBase32, byte[] qrPng) {

        @Override
        public String toString() {
            return "Provisioning[<redacted>]";
        }
    }

    /**
     * Provisions a new secret for {@code userId}, replacing any pending one.
     *
     * @throws FactorAlreadyEnrolledException when the account already holds a confirmed factor
     */
    public Provisioning provision(UUID userId, String username) {
        byte[] secret = new byte[TotpSecretCipher.SECRET_BYTES];
        random.nextBytes(secret);
        byte[] envelope = cipher.seal(userId, secret);
        transactions.executeWithoutResult(status -> {
            lockAccount(userId);
            if (factors.existsById(userId)) {
                throw new FactorAlreadyEnrolledException();
            }
            Instant now = clock.instant();
            pending.findById(userId).ifPresentOrElse(
                    row -> row.replace(envelope, cipher.keyVersion(), now),
                    () -> pending.save(new PendingTotp(userId, envelope, cipher.keyVersion(), now)));
        });
        audit.emit(AuditEvent.TOTP_ENROLMENT_PROVISIONED, AccountContext.of(userId));
        String secretBase32 = BASE32.encodeToString(secret);
        String uri = otpauthUri(username, secretBase32);
        return new Provisioning(uri, secretBase32, TotpQrCode.png(uri));
    }

    /**
     * Binds the pending secret of {@code userId} as its factor, if {@code code} verifies against it.
     *
     * @throws FactorAlreadyEnrolledException when the account already holds a confirmed factor
     * @throws InvalidFactorException         when there is no pending enrolment or the code does not verify
     */
    public void confirm(UUID userId, String code) {
        try {
            transactions.executeWithoutResult(status -> {
                lockAccount(userId);
                if (factors.existsById(userId)) {
                    throw new FactorAlreadyEnrolledException();
                }
                PendingTotp row = pending.findById(userId).orElseThrow(InvalidFactorException::new);
                byte[] secret = cipher.open(userId, row.getKeyVersion(), row.getTotpKey());
                long counter = TotpWindow.match(secret, code, clock.instant(), TotpWindow.NEVER_USED)
                        .orElseThrow(InvalidFactorException::new);
                factors.save(TotpUserDetails.boundFrom(row, counter, clock.instant()));
                pending.delete(row);
            });
        } catch (InvalidFactorException ex) {
            audit.emit(AuditEvent.TOTP_ENROLMENT_FAILED, AccountContext.of(userId));
            throw ex;
        }
        audit.emit(AuditEvent.TOTP_ENROLMENT_CONFIRMED, AccountContext.of(userId));
    }

    private void lockAccount(UUID userId) {
        accounts.findForUpdateById(userId).orElseThrow(() -> new IllegalStateException("No account " + userId));
    }

    /** The Key Uri Format: {@code otpauth://totp/issuer:username?secret=...&issuer=...}, with the RFC 6238 defaults. */
    private String otpauthUri(String username, String secretBase32) {
        String issuer = UriUtils.encode(properties.issuer(), StandardCharsets.UTF_8);
        return "otpauth://totp/" + issuer + ":" + UriUtils.encode(username, StandardCharsets.UTF_8)
                + "?secret=" + secretBase32 + "&issuer=" + issuer
                + "&algorithm=SHA1&digits=" + TotpWindow.DIGITS + "&period=" + TotpWindow.STEP_SECONDS;
    }

    /** Provisioning or confirmation while a confirmed factor exists: 409 {@code FACTOR_ALREADY_ENROLLED}. */
    public static final class FactorAlreadyEnrolledException extends RuntimeException {

        FactorAlreadyEnrolledException() {
            super("A TOTP factor is already enrolled");
        }
    }

    /** A code that did not verify, or no pending enrolment to verify it against: 412 {@code INVALID_FACTOR}. */
    public static final class InvalidFactorException extends RuntimeException {

        InvalidFactorException() {
            super("The TOTP code was not accepted");
        }
    }
}
