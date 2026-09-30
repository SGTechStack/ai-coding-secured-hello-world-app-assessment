package sg.example.helloauth.account;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One person's identity in the app. A set {@code deletedAt} makes it a Tombstone. */
@Entity
@Table(name = "users")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    private String username;

    /** The username lowercased: the key for case-insensitive lookup and uniqueness. */
    private String usernameKey;

    private String email;

    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Role role;

    private boolean enabled;

    private int failedLoginAttempts;

    private Instant lockedUntil;

    private Instant createdAt;

    private Instant deletedAt;

    protected Account() {
    }

    /** A new Account with this role, enabled and not Locked. */
    static Account newAccount(Role role, String username, String email, String passwordHash, Instant createdAt) {
        Account account = new Account();
        account.username = username;
        account.usernameKey = usernameKey(username);
        account.email = normaliseEmail(email);
        account.passwordHash = passwordHash;
        account.role = role;
        account.enabled = true;
        account.failedLoginAttempts = 0;
        account.createdAt = createdAt;
        return account;
    }

    /** The case-insensitive form of a username, as login and uniqueness compare it. */
    public static String usernameKey(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    /** The email as stored and compared: lowercased. */
    public static String normaliseEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Counts one more consecutive failed login, locking the Account once the count reaches the
     * threshold. Failures while Locked don't count, so they can't extend the lock; once a lock
     * has expired, counting starts again from zero. A Disabled Account can't log in anyway, so its
     * failures don't count either: Disabled and Locked stay independent states.
     *
     * @return whether this failure locked the Account
     */
    boolean recordFailedLogin(Instant now, LockoutPolicy policy) {
        if (!enabled || isLockedAt(now)) {
            return false;
        }
        if (lockedUntil != null) {
            lockedUntil = null;
            failedLoginAttempts = 0;
        }
        failedLoginAttempts++;
        if (failedLoginAttempts < policy.threshold()) {
            return false;
        }
        lockedUntil = now.plus(policy.duration());
        return true;
    }

    void recordSuccessfulLogin() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    /** Leaves any lock and the Failed-login counter as they are (ADR-0006). */
    void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** Disabled and Locked are independent: this leaves any lock in place. */
    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Lifts any lock and resets the Failed-login counter, so the owner can log in straight away. */
    void unlock() {
        lockedUntil = null;
        failedLoginAttempts = 0;
    }

    void changeRole(Role role) {
        this.role = role;
    }

    /**
     * Makes the Account a Tombstone (ADR-0007). The row stays, so its username and email stay
     * reserved and the audit trail keeps pointing at something.
     */
    void markDeleted(Instant now) {
        deletedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
