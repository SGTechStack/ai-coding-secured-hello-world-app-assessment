package sg.securedhello.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.PortSession;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.RestartHarness;
import sg.securedhello.testsupport.RestartHarness.Boot;

/**
 * An audit file that stops accepting writes never breaks a request: sign-in still succeeds, and the failure is written
 * to stderr and counted on {@value AppenderFailureStatusListener#COUNTER} (LOG §5:375; LOG §5:376). Its own boot
 * ({@code restart}, own database), whose audit file target starts failing every write once the application is up. (An
 * audit file that cannot even be opened is a Logback configuration error, which Boot turns into a refused startup.)
 */
@ExtendWith(OutputCaptureExtension.class)
class LoggingFailureRestartTest {

    /** Logback is one per JVM: leave it configured by an ordinary boot, with a writable audit file, for later tests. */
    @AfterEach
    void restoreLogging() {
        assertThat(RestartHarness.boot(builder -> builder.profiles("dev")).failure()).isNull();
    }

    /** The audit file appender's target, from here on a disk that refuses every write. */
    @SuppressWarnings("unchecked")
    private static void failAuditFileWrites() {
        LoggerContext logging = (LoggerContext) LoggerFactory.getILoggerFactory();
        OutputStreamAppender<ILoggingEvent> auditFile = (OutputStreamAppender<ILoggingEvent>) logging
                .getLogger(AuditEmitter.AUDIT_LOGGER).getAppender("AUDIT_FILE");
        assertThat(auditFile.isStarted()).isTrue();
        auditFile.setOutputStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("audit volume refuses writes");
            }
        });
    }

    @Test
    @Proves("T-AUD-031")
    void aSignInSucceedsWhileTheAuditFileRefusesWritesAndTheFailureIsReportedAndCounted(CapturedOutput output) {
        long before = AppenderFailureStatusListener.failures();

        Boot boot = RestartHarness.run(builder -> builder.profiles("dev"), context -> {
            Account account = new Accounts(context.getBean(JdbcTemplate.class),
                    context.getBean(PasswordEncoder.class)).user();
            failAuditFileWrites();

            PortSession signedIn = PortSession.bootstrap(PortSession.client(context)).signIn(account.username(),
                    account.password());

            assertThat(signedIn.get("/api/profile").getStatus().value()).isEqualTo(200);
            assertThat(context.getBean(MeterRegistry.class).get(AppenderFailureStatusListener.COUNTER)
                    .functionCounter().count()).isGreaterThan(before);
        });

        assertThat(boot.failure()).isNull();
        assertThat(AppenderFailureStatusListener.failures()).isGreaterThan(before);
        assertThat(output.getErr()).contains("Logging failure:", "IO failure in appender");
    }
}
