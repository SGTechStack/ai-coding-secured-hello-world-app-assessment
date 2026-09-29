package sg.securedhello.registration;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.securedhello.credential.CredentialTokenInvalidException;
import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.credential.CredentialTokens;
import sg.securedhello.password.PasswordService;
import sg.securedhello.user.UserAccount;
import sg.securedhello.user.UserAccountRepository;

/**
 * Self-registration, step two (ADR-032): redeeming the activation token sets the first password and activates the
 * account, in one transaction (ADR-007):
 * <ol>
 *   <li>consume the token, so a strength error can never confirm that a token was valid;</li>
 *   <li>set the password through {@link PasswordService}, whose rejection rolls the consume back and leaves the token
 *       redeemable;</li>
 *   <li>stamp {@code activated_at}.</li>
 * </ol>
 * The account's row is locked before the token is consumed, the lock a re-registration of the same address takes too,
 * so the two run one after the other: a re-registration that commits first has cancelled the token, and one that
 * commits second finds the account activated and leaves it alone (ADR-032; T-CRED-027).
 *
 * <p>It ends no session: the account has none yet (ADR-037), and it creates none.
 */
@Service
public class Activation {

    private final CredentialTokens tokens;
    private final PasswordService passwords;
    private final UserAccountRepository accounts;
    private final Clock clock;

    Activation(CredentialTokens tokens, PasswordService passwords, UserAccountRepository accounts, Clock clock) {
        this.tokens = tokens;
        this.passwords = passwords;
        this.accounts = accounts;
        this.clock = clock;
    }

    /**
     * Activates the account {@code token} was minted for, with {@code password}.
     *
     * @throws CredentialTokenInvalidException if the token does not redeem as an activation token
     * @throws sg.securedhello.password.PasswordRejectedException if the policy refuses the password
     */
    @Transactional
    public void activate(String token, String password) {
        // The row lock first; the consume below clears the persistence context, so the account is read again after.
        UUID accountId = tokens.holder(CredentialTokenType.ACTIVATION, token)
                .flatMap(accounts::findForUpdateById)
                .filter(UserAccount::isPending)
                .map(UserAccount::getId)
                .orElseThrow(CredentialTokenInvalidException::new);
        if (tokens.redeem(CredentialTokenType.ACTIVATION, token).isEmpty()) {
            throw new CredentialTokenInvalidException();
        }
        passwords.setPassword(accountId, password);
        accounts.findById(accountId).orElseThrow(CredentialTokenInvalidException::new)
                .activate(clock.instant().truncatedTo(ChronoUnit.MICROS));
    }
}
