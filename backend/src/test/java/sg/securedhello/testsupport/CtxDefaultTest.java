package sg.securedhello.testsupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code ctx-default}: the full application context driven through MockMvc (level C). The default home for
 * security-control tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class CtxDefaultTest extends SharedContextTest {

    @Autowired
    protected MockMvc mockMvc;
}
