package sg.securedhello.admin;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import sg.securedhello.config.AdminSeedProperties;
import sg.securedhello.password.PasswordService;
import sg.securedhello.user.Identifiers;
import sg.securedhello.user.Tombstones;
import sg.securedhello.user.UserAccount;
import sg.securedhello.user.UserAccountRepository;

/**
 * The first administrator, from {@code APP_ADMIN_USERNAME} and {@code APP_ADMIN_PASSWORD} (ADR-047). Not
 * profile-gated: what is gated is the secret, which comes from the environment only.
 *
 * <p><b>Validation runs at context refresh</b>, in the constructor, so a bad value stops the application before the
 * web server opens its port (T-ADM-023): the username must be a valid, non-reserved username ({@link Identifiers},
 * one set with registration, T-ADM-022), and the password must pass the policy {@link PasswordService} runs on every
 * password. A refusal names the property and the rule, never the password.
 *
 * <p><b>Seeding runs in the runner</b>, once, in one transaction:
 * <ul>
 *   <li>any {@code ADMIN} row, enabled or disabled, means no seed. A disabled admin is never seeded around: minting a
 *       way in around a deliberate disable is the hole, not the fix (T-ADM-024; T-ADM-025);</li>
 *   <li>a tombstone holding the username fails startup, because the tombstone legitimately blocks it (T-ADM-026), and
 *       so does a live account already holding it;</li>
 *   <li>otherwise one enabled, activated {@code ADMIN} is created and given a <em>forced-change credential</em>
 *       through {@link PasswordService#issueForcedChangeCredential}: {@code force_password_change} set and
 *       {@code credential_issued_at} stamped, so it expires 30 days after issue if unused (ADR-046).</li>
 * </ul>
 * The seed has no mailbox: its address is {@code <username>@}{@value #SEED_EMAIL_DOMAIN}, a reserved,
 * never-deliverable domain (RFC 2606), which only fills the mandatory, unique {@code email} column.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    /** The domain of the seeded administrator's placeholder address. */
    static final String SEED_EMAIL_DOMAIN = "admin.invalid";

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final String username;
    private final String password;
    private final UserAccountRepository accounts;
    private final Tombstones tombstones;
    private final PasswordService passwords;
    private final Clock clock;

    AdminBootstrap(AdminSeedProperties seed, UserAccountRepository accounts, Tombstones tombstones,
            PasswordService passwords, Clock clock) {
        this.username = seed.username();
        this.password = seed.password();
        this.accounts = accounts;
        this.tombstones = tombstones;
        this.passwords = passwords;
        this.clock = clock;
        if (!Identifiers.validUsername(username)) {
            throw new IllegalStateException("app.admin.username is not a valid username: it must be lower case,"
                    + " match [a-z0-9._-]{3,32} and not be a reserved name (ADR-047)");
        }
        passwords.rejection(password, username, email()).ifPresent(rule -> {
            throw new IllegalStateException("app.admin.password is refused by the password policy: rule " + rule
                    + " (ADR-047)");
        });
    }

    private String email() {
        return username + "@" + SEED_EMAIL_DOMAIN;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (accounts.existsByRole("ADMIN")) {
            log.info("Administrator bootstrap: an ADMIN account exists, so none is seeded");
            return;
        }
        if (tombstones.holdsUsername(username)) {
            throw new IllegalStateException("app.admin.username names a deleted account; its tombstone blocks the"
                    + " seed. Choose another username (ADR-047)");
        }
        if (accounts.findByUsername(username).isPresent() || accounts.findByEmail(email()).isPresent()) {
            throw new IllegalStateException("app.admin.username is held by an existing non-admin account; choose"
                    + " another username (ADR-047)");
        }
        UserAccount admin = accounts.save(UserAccount.administrator(username, email(), clock.instant()));
        passwords.issueForcedChangeCredential(admin.getId(), password);
        log.info("Administrator bootstrap: seeded user.id={} with a forced-change credential", admin.getId());
    }
}
