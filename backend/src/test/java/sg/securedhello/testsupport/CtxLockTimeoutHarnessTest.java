package sg.securedhello.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CtxLockTimeoutHarnessTest extends CtxLockTimeoutTest {

    @Autowired
    DataSource dataSource;

    @Test
    void usesATemporaryH2FileWithAShortLockTimeout() throws Exception {
        assertThat(H2Probe.lockTimeoutMillis(dataSource)).isEqualTo(50);
        H2Probe.assertOnTemporaryFile(dataSource);
    }
}
