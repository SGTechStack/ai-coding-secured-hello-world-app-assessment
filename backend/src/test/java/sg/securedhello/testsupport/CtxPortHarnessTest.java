package sg.securedhello.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CtxPortHarnessTest extends CtxPortTest {

    @Autowired
    DataSource dataSource;

    @Test
    void servesHealthOnARandomRealPort() {
        assertThat(port).isPositive().isNotEqualTo(8080);

        restClient.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"status\":\"UP\"}");
    }

    @Test
    void usesATemporaryH2FileWithTheProductionLockTimeout() throws Exception {
        assertThat(H2Probe.lockTimeoutMillis(dataSource)).isEqualTo(1000);
        H2Probe.assertOnTemporaryFile(dataSource);
    }
}
