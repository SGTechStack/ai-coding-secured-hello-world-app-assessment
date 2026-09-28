package sg.securedhello.testsupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * {@code ctx-port}: the full application context on a random real port (level P). Use it only where MockMvc bypasses
 * Tomcat: {@code RemoteIpValve}, raw {@code Set-Cookie}, duplicate cookies, header budgets and Tomcat meters.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
public abstract class CtxPortTest extends SharedContextTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected RestTestClient restClient;
}
