package sg.securedhello.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.RestartHarness;
import sg.securedhello.testsupport.RestartHarness.Boot;
import sg.securedhello.testsupport.SessionRows;

/**
 * The scheduled cleanup, which the shared contexts switch off ({@code -}), deletes expired {@code SPRING_SESSION} rows
 * and leaves live ones (R-SES-011; ADR-041). Its own boot ({@code restart}, own database) runs the job every second
 * instead of every minute, so the test waits a second, not a minute; the production cron is T-SES-028's.
 */
class SessionCleanupCronTest {

    @Test
    @Proves("T-SES-023")
    void theScheduledCleanupDeletesExpiredRowsAndKeepsLiveOnes() {
        Boot boot = RestartHarness.run(builder -> builder.profiles("dev"), context -> {
            JdbcIndexedSessionRepository repository = context.getBean(JdbcIndexedSessionRepository.class);
            SessionRows rows = new SessionRows(context.getBean(JdbcTemplate.class));
            String expired = saved(repository);
            String live = saved(repository);
            rows.expire(expired);

            await().atMost(Duration.ofSeconds(15)).until(() -> !rows.exists(expired));
            assertThat(rows.exists(live)).as("the live session").isTrue();
        }, "--spring.session.jdbc.cleanup-cron=* * * * * *");

        assertThat(boot.failure()).isNull();
    }

    private static <S extends Session> String saved(SessionRepository<S> repository) {
        S session = repository.createSession();
        repository.save(session);
        return session.getId();
    }
}
