package sg.securedhello.testsupport;

import java.time.Clock;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;

/**
 * What every shared Spring context has in common (ADR-065, ADR-066, ADR-067):
 * <ul>
 *   <li>the real {@code dev} profile, never a {@code test} profile;</li>
 *   <li>a fresh temporary H2 file;</li>
 *   <li>BCrypt cost 4, a test-speed setting that does not touch the security posture;</li>
 *   <li>the {@code clock} bean replaced by the one suite-wide {@link MutableClock}, exposed as {@link #clock}.</li>
 * </ul>
 *
 * <p>Extend one of the named contexts ({@link CtxDefaultTest}, {@link CtxPortTest}, {@link CtxLockTimeoutTest}),
 * never this class directly. Adding a property, {@code @MockitoBean} or {@code @TestBean} to a subclass creates a
 * new cached context and needs a stated reason.
 */
@ActiveProfiles("dev")
@ContextConfiguration(initializers = TemporaryH2FileInitializer.class)
@TestPropertySource(properties = "app.security.password.bcrypt-strength=4")
public abstract class SharedContextTest {

    /** The suite-wide forward-only clock, which is also the context's {@code clock} bean. Advance it; never sleep. */
    protected final MutableClock clock = TestClock.shared();

    /** Replaces the one {@code Clock} bean; typed {@code Clock} because a bean override must match the bean's type. */
    @TestBean(methodName = "sg.securedhello.testsupport.TestClock#shared")
    private Clock clockBean;
}
