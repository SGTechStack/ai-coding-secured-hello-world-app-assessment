package sg.securedhello.testsupport;

import java.time.Clock;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;

import sg.securedhello.email.EmailService;

/**
 * What every shared Spring context has in common (ADR-065, ADR-066, ADR-067):
 * <ul>
 *   <li>the real {@code dev} profile, never a {@code test} profile;</li>
 *   <li>a fresh temporary H2 file, and the test-only {@link TestSecrets}, both from
 *       {@link TemporaryH2FileInitializer};</li>
 *   <li>BCrypt cost 4, a test-speed setting that does not touch the security posture;</li>
 *   <li>the session cleanup job off ({@code -}), so rows disappear only when a test acts;</li>
 *   <li>OTLP metrics export off (REJ-068);</li>
 *   <li>the dev demo accounts off, so no test meets an account it did not create;</li>
 *   <li>the {@code clock} bean replaced by the one suite-wide {@link MutableClock}, exposed as {@link #clock};</li>
 *   <li>the {@code EmailService} bean replaced by the suite-wide {@link CapturedEmails}, exposed as {@link #emails}.</li>
 * </ul>
 *
 * <p>Extend one of the named contexts ({@link CtxDefaultTest}, {@link CtxPortTest}, {@link CtxLockTimeoutTest},
 * {@link CtxBudgetTest}), never this class directly. Adding a property, {@code @MockitoBean} or {@code @TestBean} to a subclass creates a
 * new cached context and needs a stated reason.
 */
@ActiveProfiles("dev")
@ContextConfiguration(initializers = TemporaryH2FileInitializer.class)
@TestPropertySource(properties = {"app.security.password.bcrypt-strength=4",
        // No background session cleanup: it runs on Spring Session's own clock and would race row-counting tests.
        "spring.session.jdbc.cleanup-cron=-",
        // Metrics export stays off whatever a profile says, so no test pushes to a collector (REJ-068).
        "management.otlp.metrics.export.enabled=false",
        // No dev demo accounts: tests own every account they count (DemoAccountsTest turns them on).
        "app.dev.demo-accounts.enabled=false"})
public abstract class SharedContextTest {

    /**
     * The raised rate-limit budgets every shared context except {@link CtxBudgetTest} loads, so the suite's fixture
     * traffic from the one loopback source never spends a budget. The file says why.
     */
    public static final String HARNESS_BUDGETS = "classpath:harness-budgets.properties";

    /** The suite-wide forward-only clock, which is also the context's {@code clock} bean. Advance it; never sleep. */
    protected final MutableClock clock = TestClock.shared();

    /** Replaces the one {@code Clock} bean; typed {@code Clock} because a bean override must match the bean's type. */
    @TestBean(methodName = "sg.securedhello.testsupport.TestClock#shared")
    private Clock clockBean;

    /** The suite-wide email capture: tests read activation and reset tokens from it (ADR-067). */
    protected final CapturedEmails emails = CapturedEmails.shared();

    /** Replaces the dev link logger with {@link #emails}, the one fixed override of ADR-067. */
    @TestBean(methodName = "sg.securedhello.testsupport.CapturedEmails#shared")
    private EmailService emailServiceBean;
}
