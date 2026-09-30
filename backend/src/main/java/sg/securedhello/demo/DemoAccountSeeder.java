package sg.securedhello.demo;

import static sg.securedhello.config.PublishedDemoValues.DEMO_ADMIN;
import static sg.securedhello.config.PublishedDemoValues.DEMO_USER;
import static sg.securedhello.config.PublishedDemoValues.demoEmail;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import sg.securedhello.mfa.DemoTotpFactor;
import sg.securedhello.password.PasswordService;
import sg.securedhello.user.Tombstones;
import sg.securedhello.user.UserAccount;
import sg.securedhello.user.UserAccountRepository;

/**
 * Seeds the two demo accounts on a {@code dev} start, for demos: {@code demo-user}, an activated
 * {@code USER}, and {@code demo-admin}, an activated {@code ADMIN} pre-enrolled in TOTP, so an
 * enrolled, authenticable administrator (ADR-048) from the first start. Both get their committed password through
 * {@link PasswordService#setPassword}, so the policy runs and no change is forced.
 *
 * <ul>
 *   <li>Only under {@code dev}, only in the web application (the recovery runner never seeds, ADR-072), and only while
 *       {@code app.dev.demo-accounts.enabled} is true.</li>
 *   <li>It runs before {@code AdminBootstrap}, so on an empty database the demo administrator is the dev seed, and the
 *       bootstrap, finding an {@code ADMIN}, seeds none. Outside dev the bootstrap is unchanged.</li>
 *   <li>Idempotent: an account whose username or address is taken, or held by a tombstone, is left as it is, so a
 *       restart, a changed password or a deletion is never undone.</li>
 * </ul>
 */
@Component
@Profile("dev")
@ConditionalOnWebApplication
@ConditionalOnBooleanProperty("app.dev.demo-accounts.enabled")
@Order(Ordered.HIGHEST_PRECEDENCE)
class DemoAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountSeeder.class);

    private final DemoAccountsProperties demo;
    private final UserAccountRepository accounts;
    private final Tombstones tombstones;
    private final PasswordService passwords;
    private final DemoTotpFactor factors;
    private final Clock clock;

    DemoAccountSeeder(DemoAccountsProperties demo, UserAccountRepository accounts, Tombstones tombstones,
            PasswordService passwords, DemoTotpFactor factors, Clock clock) {
        this.demo = demo;
        this.accounts = accounts;
        this.tombstones = tombstones;
        this.passwords = passwords;
        this.factors = factors;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (absent(DEMO_USER)) {
            Instant now = clock.instant();
            UserAccount user = accounts.save(UserAccount.pendingRegistration(DEMO_USER, demoEmail(DEMO_USER), now));
            passwords.setPassword(user.getId(), demo.userPassword());
            // Reloaded: setting the password clears the persistence context.
            accounts.findById(user.getId()).orElseThrow().activate(now);
            log.info("Demo accounts: seeded {}", DEMO_USER);
        }
        if (absent(DEMO_ADMIN)) {
            UserAccount admin = accounts.save(
                    UserAccount.administrator(DEMO_ADMIN, demoEmail(DEMO_ADMIN), clock.instant()));
            passwords.setPassword(admin.getId(), demo.adminPassword());
            factors.enrol(admin.getId(), demo.adminTotpSecretBytes());
            log.info("Demo accounts: seeded {}, enrolled in TOTP", DEMO_ADMIN);
        }
    }

    private boolean absent(String username) {
        String email = demoEmail(username);
        boolean absent = accounts.findByUsername(username).isEmpty() && accounts.findByEmail(email).isEmpty()
                && !tombstones.holdsUsername(username) && !tombstones.holdsEmail(email);
        if (!absent) {
            log.info("Demo accounts: {} exists or is held by a tombstone, so it is left as it is", username);
        }
        return absent;
    }
}
