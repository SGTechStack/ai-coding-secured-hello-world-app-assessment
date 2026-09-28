package sg.securedhello.testsupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code ctx-locktimeout}: {@code ctx-default} with a 50 ms H2 lock timeout, for the one test that really waits on a
 * row lock (ADR-066). Everything else belongs in {@link CtxDefaultTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = TemporaryH2FileInitializer.LOCK_TIMEOUT_PROPERTY + "=50")
public abstract class CtxLockTimeoutTest extends SharedContextTest {

    @Autowired
    protected MockMvc mockMvc;
}
