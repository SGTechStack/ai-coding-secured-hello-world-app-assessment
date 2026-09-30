package sg.securedhello.profile;

import java.io.Serial;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.securedhello.password.PasswordPolicy;
import sg.securedhello.password.PasswordService;
import sg.securedhello.session.SessionTerminationService;
import sg.securedhello.user.UserAccount;
import sg.securedhello.user.UserAccountRepository;

/**
 * Self-service password change and forced-change completion (ADR-008), one transaction for the state change:
 * <ol>
 *   <li>verify the current password with {@code matches()} directly, never through the {@code AuthenticationManager},
 *       so a wrong guess publishes no authentication event and never moves the lockout counter;</li>
 *   <li>set the new one through {@link PasswordService}, which validates it, writes history and (step 3) invalidates
 *       pending reset tokens;</li>
 *   <li>end every other session of the account, dispatched after commit (ADR-035; ADR-039).</li>
 * </ol>
 * The controller then rotates the surviving session's id (step 5). Owner notification (step 6) is not built.
 */
@Service
public class PasswordChange {

    private static final Logger log = LoggerFactory.getLogger(PasswordChange.class);

    private final UserAccountRepository accounts;
    private final PasswordEncoder encoder;
    private final PasswordService passwords;
    private final SessionTerminationService sessions;

    PasswordChange(UserAccountRepository accounts, PasswordEncoder encoder, PasswordService passwords,
            SessionTerminationService sessions) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.passwords = passwords;
        this.sessions = sessions;
    }

    /**
     * Changes the password of {@code accountId}, keeping only the session {@code actingSessionId}.
     *
     * @throws CurrentPasswordMismatchException if {@code currentPassword} is not the account's password
     * @throws sg.securedhello.password.PasswordRejectedException if the policy refuses {@code newPassword}
     */
    @Transactional
    public void change(UUID accountId, String currentPassword, String newPassword, String actingSessionId) {
        UserAccount account = accounts.findById(accountId).orElseThrow(CurrentPasswordMismatchException::new);
        String hash = account.getPasswordHash();
        if (hash == null || !encoder.matches(PasswordPolicy.normalise(currentPassword), hash)) {
            log.warn("Password change refused: the current password did not match (user.id={})", accountId);
            throw new CurrentPasswordMismatchException();
        }
        // The row lock every trusted-device write takes (ADR-075): the change revokes the account's devices.
        accounts.findForUpdateById(accountId);
        passwords.setPassword(accountId, newPassword);
        sessions.endAllExcept(account.getUsername(), actingSessionId);
    }

    /** The submitted current password is wrong: 400 {@code VALIDATION_FAILED}, and nothing changes. */
    public static final class CurrentPasswordMismatchException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        CurrentPasswordMismatchException() {
            super("The current password did not match");
        }
    }
}
