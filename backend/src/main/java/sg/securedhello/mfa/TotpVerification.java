package sg.securedhello.mfa;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import sg.securedhello.audit.AccountContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.mfa.TotpEnrolment.InvalidFactorException;
import sg.securedhello.user.UserAccountRepository;

/**
 * Factor verification (ADR-021): a code checked against the enrolled secret, which grants or renews
 * {@code FACTOR_TOTP}. The account is the signed-in one, never one named in the request (ADR-027).
 *
 * <p>It takes the {@code users} row lock, then the TOTP row lock, the lock order everywhere (spec, Sessions), and holds
 * the TOTP row across load, decrypt, compare and write, so two requests carrying one code cannot both verify. A code
 * is accepted only for a counter after the last accepted one, so a verified code, and every earlier one, is a replay
 * (R-MFA-017; RFC 6238 §5.2). A lock timeout fails the request; it never grants.
 */
@Service
public class TotpVerification {

    private final UserAccountRepository accounts;
    private final TotpUserDetailsRepository factors;
    private final TotpSecretCipher cipher;
    private final AuditEmitter audit;
    private final Clock clock;
    private final TransactionTemplate transactions;

    TotpVerification(UserAccountRepository accounts, TotpUserDetailsRepository factors, TotpSecretCipher cipher,
            AuditEmitter audit, Clock clock, PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.factors = factors;
        this.cipher = cipher;
        this.audit = audit;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Verifies {@code code} for {@code userId} and records its counter as used.
     *
     * @throws FactorEnrolmentRequiredException when the account has no enrolled factor
     * @throws FactorDisabledException          when tier 2 has disabled the factor, before any code is checked
     * @throws InvalidFactorException           when the code does not verify, including a replay
     */
    public void verify(UUID userId, String code) {
        try {
            transactions.executeWithoutResult(status -> {
                accounts.findForUpdateById(userId)
                        .orElseThrow(() -> new IllegalStateException("No account " + userId));
                TotpUserDetails factor = factors.findForUpdateByUserId(userId)
                        .orElseThrow(FactorEnrolmentRequiredException::new);
                if (factor.isDisabled()) {
                    throw new FactorDisabledException();
                }
                byte[] secret = cipher.open(userId, factor.getKeyVersion(), factor.getTotpKey());
                long counter = TotpWindow.match(secret, code, clock.instant(), factor.lastUsedCounter())
                        .orElseThrow(InvalidFactorException::new);
                factor.accept(counter);
            });
        } catch (InvalidFactorException ex) {
            audit.emit(AuditEvent.TOTP_VERIFICATION_FAILED, AccountContext.of(userId));
            throw ex;
        }
        audit.emit(AuditEvent.TOTP_VERIFIED, AccountContext.of(userId));
    }

    /** A factor tier 2 disabled: 423 {@code FACTOR_DISABLED} until it is rebound (ADR-027; R-MFA-006). */
    public static final class FactorDisabledException extends RuntimeException {

        FactorDisabledException() {
            super("The TOTP factor is disabled");
        }
    }

    /** An administrator with no enrolled factor: 422 {@code FACTOR_ENROLMENT_REQUIRED} (R-MFA-002). */
    public static final class FactorEnrolmentRequiredException extends RuntimeException {

        FactorEnrolmentRequiredException() {
            super("No TOTP factor is enrolled");
        }
    }
}
