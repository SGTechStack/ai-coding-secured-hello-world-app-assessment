package sg.example.helloauth.account;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.example.helloauth.api.ApiException;
import sg.example.helloauth.api.ErrorCode;

/** The Account module's interface. "Active" means not a Tombstone. */
@Service
public class AccountService {

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    AccountService(AccountRepository accounts, PasswordEncoder passwordEncoder, Clock clock) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Creates a Regular user. A clash on username or email, Tombstones included, gets one combined
     * error that doesn't say which field clashed (ADR-0005).
     */
    @Transactional
    public Account register(String username, String email, String password) {
        return create(Role.USER, username, email, password);
    }

    /** Creates an Admin, as only the Bootstrap admin is created; the same clash rules apply. */
    @Transactional
    Account createAdmin(String username, String email, String password) {
        return create(Role.ADMIN, username, email, password);
    }

    /** One page of the Accounts that aren't Tombstones, oldest first. */
    @Transactional(readOnly = true)
    public Page<Account> listActive(int page, int size) {
        return accounts.findByDeletedAtIsNullOrderByCreatedAtAscUsernameKeyAsc(PageRequest.of(page, size));
    }

    /** Whether any Admin exists that isn't a Tombstone; a Disabled one counts. */
    @Transactional(readOnly = true)
    public boolean hasActiveAdmin() {
        return accounts.existsByRoleAndDeletedAtIsNull(Role.ADMIN);
    }

    private Account create(Role role, String username, String email, String password) {
        if (accounts.existsByUsernameKeyOrEmail(Account.usernameKey(username), Account.normaliseEmail(email))) {
            throw userExists();
        }
        Account account = Account.newAccount(role, username, email, passwordEncoder.encode(password), clock.instant());
        try {
            return accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            // A concurrent registration took the username or email after the check above.
            // Any other integrity failure is a real error, not a clash.
            if (isUniqueKeyViolation(ex)) {
                throw userExists();
            }
            throw ex;
        }
    }

    private static boolean isUniqueKeyViolation(DataIntegrityViolationException ex) {
        return ex.getCause() instanceof ConstraintViolationException violation
                && violation.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE;
    }

    @Transactional(readOnly = true)
    public Optional<Account> findActiveByUsername(String username) {
        return accounts.findByUsernameKeyAndDeletedAtIsNull(Account.usernameKey(username));
    }

    @Transactional(readOnly = true)
    public Optional<Account> findActiveById(UUID id) {
        return accounts.findByIdAndDeletedAtIsNull(id);
    }

    /**
     * Finds the active Account with this email, matched regardless of case as it is stored
     * lowercased, and holds its row until the caller's transaction ends.
     */
    @Transactional
    public Optional<Account> lockActiveByEmail(String email) {
        return accounts.findForUpdateByEmailAndDeletedAtIsNull(Account.normaliseEmail(email));
    }

    /**
     * Sets a new password on the active Account with this id. The caller has already checked it
     * against the Password policy. Any lock and the Failed-login counter stay as they are.
     *
     * @return the Account, or empty if there is no active Account with this id
     */
    @Transactional
    public Optional<Account> changePassword(UUID id, String newPassword) {
        return change(id, account -> account.changePasswordHash(passwordEncoder.encode(newPassword)));
    }

    /**
     * Counts a failed login against the active Account with this username, if there is one.
     *
     * @return the Account, if this failure locked it
     */
    @Transactional
    public Optional<Account> recordFailedLogin(String username, LockoutPolicy policy) {
        Optional<Account> account = accounts.findForUpdateByUsernameKeyAndDeletedAtIsNull(Account.usernameKey(username));
        if (account.isPresent() && account.get().recordFailedLogin(clock.instant(), policy)) {
            return account;
        }
        return Optional.empty();
    }

    /** Resets the Failed-login counter and clears an expired lock. */
    @Transactional
    public void recordSuccessfulLogin(UUID id) {
        accounts.findById(id).ifPresent(Account::recordSuccessfulLogin);
    }

    /**
     * Disables or re-enables the active Account with this id. Ending its sessions is the
     * caller's job.
     *
     * @return the Account, or empty if there is no active Account with this id
     */
    @Transactional
    public Optional<Account> setEnabled(UUID id, boolean enabled) {
        return change(id, account -> account.setEnabled(enabled));
    }

    /** Lifts any lock on the active Account with this id and resets its Failed-login counter. */
    @Transactional
    public Optional<Account> unlock(UUID id) {
        return change(id, Account::unlock);
    }

    /** Ending the Account's sessions, so its old role goes with them, is the caller's job. */
    @Transactional
    public Optional<Account> changeRole(UUID id, Role role) {
        return change(id, account -> account.changeRole(role));
    }

    /**
     * Turns the active Account with this id into a Tombstone. Ending its sessions and removing
     * its Password reset tokens is the caller's job.
     */
    @Transactional
    public Optional<Account> softDelete(UUID id) {
        return change(id, account -> account.markDeleted(clock.instant()));
    }

    private Optional<Account> change(UUID id, Consumer<Account> change) {
        Optional<Account> account = accounts.findByIdAndDeletedAtIsNull(id);
        account.ifPresent(change);
        return account;
    }

    private static ApiException userExists() {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.USER_EXIST,
                "The username or email is already registered.");
    }
}
