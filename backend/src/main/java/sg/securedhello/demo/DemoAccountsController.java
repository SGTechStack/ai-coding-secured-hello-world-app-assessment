package sg.securedhello.demo;

import static sg.securedhello.config.PublishedDemoValues.DEMO_ADMIN;
import static sg.securedhello.config.PublishedDemoValues.DEMO_USER;
import static sg.securedhello.config.PublishedDemoValues.demoEmail;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import sg.securedhello.mfa.DemoTotpFactor;
import sg.securedhello.mfa.TotpWindow;
import sg.securedhello.user.UserAccountRepository;

/**
 * {@code GET /api/dev/demo-accounts}: the sign-in page's demo panel, under {@code dev} only. Outside dev this handler is
 * never registered, so its whitelist row finds no handler and the route answers 404.
 *
 * <p>It lists each seeded demo account that exists (username and seeded address): its role and committed password,
 * or {@code passwordChanged} and no password once the password has changed; and, for the administrator whose factor
 * still holds the committed secret, the current six-digit code and the seconds left in its 30-second step, computed
 * here from the secret and the application {@link Clock} with {@link TotpWindow}. The response is {@code no-store}.
 */
@RestController
@Profile("dev")
class DemoAccountsController {

    /** Whether a stored hash still matches its committed password, by hash, so BCrypt runs once per hash. */
    private final Map<String, Boolean> committedByHash = new ConcurrentHashMap<>();

    private final DemoAccountsProperties demo;
    private final UserAccountRepository accounts;
    private final PasswordEncoder encoder;
    private final DemoTotpFactor factors;
    private final Clock clock;

    DemoAccountsController(DemoAccountsProperties demo, UserAccountRepository accounts, PasswordEncoder encoder,
            DemoTotpFactor factors, Clock clock) {
        this.demo = demo;
        this.accounts = accounts;
        this.encoder = encoder;
        this.factors = factors;
        this.clock = clock;
    }

    /** The panel's data: the demo accounts that exist, user first. */
    record DemoAccounts(List<DemoAccount> accounts) {
    }

    /**
     * One demo account.
     *
     * @param password        the committed password, or null once it has changed
     * @param passwordChanged whether the stored password is no longer the committed one
     * @param totp            the administrator's current code, or null for the user or a re-enrolled factor
     */
    record DemoAccount(String username, String role, @Nullable String password, boolean passwordChanged,
            @Nullable CurrentCode totp) {
    }

    /** The current TOTP code and the whole seconds until its step ends. */
    record CurrentCode(String code, long secondsRemaining) {
    }

    @GetMapping("/api/dev/demo-accounts")
    ResponseEntity<DemoAccounts> demoAccounts() {
        List<DemoAccount> listed = new ArrayList<>();
        describe(DEMO_USER, demo.userPassword(), false).ifPresent(listed::add);
        describe(DEMO_ADMIN, demo.adminPassword(), true).ifPresent(listed::add);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new DemoAccounts(List.copyOf(listed)));
    }

    /** The seeded account {@code username} (its seeded address too), if it exists; the code for the administrator. */
    private Optional<DemoAccount> describe(String username, String committed, boolean admin) {
        return accounts.findByUsername(username).filter(account -> demoEmail(username).equals(account.getEmail()))
                .map(account -> {
                    String hash = account.getPasswordHash();
                    boolean unchanged = hash != null
                            && committedByHash.computeIfAbsent(hash, key -> encoder.matches(committed, key));
                    CurrentCode code = admin && factors.holds(account.getId(), demo.adminTotpSecretBytes())
                            ? currentCode() : null;
                    return new DemoAccount(username, account.getRole(), unchanged ? committed : null, !unchanged,
                            code);
                });
    }

    private CurrentCode currentCode() {
        Instant now = clock.instant();
        long counter = TotpWindow.counter(now);
        long stepEnds = (counter + 1) * TotpWindow.STEP_SECONDS;
        return new CurrentCode(TotpWindow.generate(demo.adminTotpSecretBytes(), counter, TotpWindow.DIGITS),
                stepEnds - now.getEpochSecond());
    }
}
