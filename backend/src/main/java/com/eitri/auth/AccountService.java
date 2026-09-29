package com.eitri.auth;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * The account module's public API. Other features create and manage accounts through it instead of
 * reaching into the entity or the repository. Passwords are always hashed with the application's
 * {@link PasswordEncoder} (BCrypt) and must already satisfy {@link PasswordPolicy}.
 */
@Service
public class AccountService {

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SessionRevocation sessionRevocation;

    AccountService(
            AccountRepository accounts,
            PasswordEncoder passwordEncoder,
            Clock clock,
            SessionRevocation sessionRevocation) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.sessionRevocation = sessionRevocation;
    }

    public boolean adminExists() {
        return accounts.existsByRole(Role.ADMIN);
    }

    /** The account registered under {@code email}, compared lower-case. */
    public Optional<AccountView> findByEmail(String email) {
        return accounts.findByEmail(normalize(email)).map(AccountView::of);
    }

    /**
     * Replaces the account's password hash. Joins the caller's transaction, so a failure rolls back the
     * caller's other changes too. Lockout state is left as it is.
     *
     * @return whether the account exists
     */
    @Transactional
    public boolean changePassword(UUID accountId, String rawPassword) {
        if (PasswordPolicy.violation(rawPassword).isPresent()) {
            throw new IllegalArgumentException("password does not meet the password policy");
        }
        return accounts.findById(accountId)
                .map(account -> {
                    account.changePasswordHash(passwordEncoder.encode(rawPassword));
                    return true;
                })
                .orElse(false);
    }

    /** Ends every server-side session of the account, if it exists. */
    public void endAllSessions(UUID accountId) {
        accounts.findById(accountId).ifPresent(account -> sessionRevocation.revokeAll(account.getUsername()));
    }

    public Optional<AccountView> find(UUID accountId) {
        return accounts.findById(accountId).map(AccountView::of);
    }

    /** Every account, oldest first, then by username. */
    public List<AccountView> list() {
        return accounts.findAllByOrderByCreatedAtAscUsernameAsc().stream().map(AccountView::of).toList();
    }

    /**
     * An admin enables or disables another account. Disabling ends the target's sessions once committed.
     *
     * @throws AccountNotFoundException if the target does not exist
     * @throws NotAnAdminException if the actor is no longer an enabled admin
     */
    @Transactional
    public AccountView setEnabled(UUID actorId, UUID targetId, boolean enabled) {
        Account target = lockForAdminAction(actorId, targetId);
        target.setEnabled(enabled);
        if (!enabled) {
            revokeSessionsAfterCommit(target.getUsername());
        }
        return AccountView.of(target);
    }

    /**
     * An admin changes another account's role. The target's live sessions pick it up on their next
     * request through {@link AccountRefreshFilter}.
     */
    @Transactional
    public RoleChange changeRole(UUID actorId, UUID targetId, Role role) {
        Account target = lockForAdminAction(actorId, targetId);
        Role from = target.getRole();
        target.changeRole(role);
        return new RoleChange(from, AccountView.of(target));
    }

    /**
     * An admin deletes another account; its reset tokens cascade and its sessions end once committed.
     *
     * @return the account as it was just before deletion
     */
    @Transactional
    public AccountView delete(UUID actorId, UUID targetId) {
        Account target = lockForAdminAction(actorId, targetId);
        AccountView deleted = AccountView.of(target);
        accounts.delete(target);
        accounts.flush();
        revokeSessionsAfterCommit(target.getUsername());
        return deleted;
    }

    public record RoleChange(Role from, AccountView account) {}

    /**
     * Locks actor and target rows in id order (no deadlock between opposing actions) and re-checks the
     * actor inside the transaction, so two admins demoting or disabling each other at once cannot both
     * succeed and leave no admin.
     */
    private Account lockForAdminAction(UUID actorId, UUID targetId) {
        if (actorId.equals(targetId)) {
            throw new IllegalArgumentException("an admin action cannot target the actor's own account");
        }
        boolean actorFirst = actorId.compareTo(targetId) < 0;
        Optional<Account> first = accounts.findByIdForUpdate(actorFirst ? actorId : targetId);
        Optional<Account> second = accounts.findByIdForUpdate(actorFirst ? targetId : actorId);
        Optional<Account> actor = actorFirst ? first : second;
        Optional<Account> target = actorFirst ? second : first;
        if (actor.isEmpty() || !actor.get().isEnabled() || actor.get().getRole() != Role.ADMIN) {
            throw new NotAnAdminException();
        }
        return target.orElseThrow(AccountNotFoundException::new);
    }

    private void revokeSessionsAfterCommit(String username) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sessionRevocation.revokeAll(username);
            }
        });
    }

    /**
     * Creates an enabled account with the given role, storing username and email lower-case.
     *
     * @throws AccountConflictException if the username (checked first) or email is taken
     */
    public AccountView create(String username, String email, String rawPassword, Role role) {
        String normalizedUsername = normalize(username);
        String normalizedEmail = normalize(email);
        if (PasswordPolicy.violation(rawPassword).isPresent()) {
            throw new IllegalArgumentException("password does not meet the password policy");
        }
        requireAvailable(normalizedUsername, normalizedEmail);

        Account account = new Account(
                UUID.randomUUID(),
                normalizedUsername,
                normalizedEmail,
                passwordEncoder.encode(rawPassword),
                role,
                clock.instant());
        try {
            return AccountView.of(accounts.saveAndFlush(account));
        } catch (DataIntegrityViolationException raced) {
            // A concurrent request took the username or email between the check and the insert.
            requireAvailable(normalizedUsername, normalizedEmail);
            throw raced;
        }
    }

    private void requireAvailable(String username, String email) {
        if (accounts.existsByUsername(username)) {
            throw new AccountConflictException(AccountConflictException.Field.USERNAME);
        }
        if (accounts.existsByEmail(email)) {
            throw new AccountConflictException(AccountConflictException.Field.EMAIL);
        }
    }

    static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
