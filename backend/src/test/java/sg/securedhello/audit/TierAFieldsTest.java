package sg.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.rolling.RollingFileAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.EcsJson;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;
import sg.securedhello.testsupport.TotpFactors;

/**
 * The Tier A fields on every line a request writes, on stdout and in the audit file (LOG §5:347; LOG §5:348): across a
 * sign-in, an admin mutation and an error flow. MockMvc runs each request on the test's thread, so the lines checked
 * are the ones written on it; background threads (the pool's housekeeping) belong to no request.
 */
@ExtendWith(OutputCaptureExtension.class)
class TierAFieldsTest extends CtxDefaultTest {

    private static final List<String> TIER_A = List.of("@timestamp", "message", "log.level", "log.logger",
            "ecs.version", "process.thread.name", "service.name", "service.version", "service.environment",
            "trace.id", "span.id");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    @Test
    @Proves("T-AUD-001")
    void everyLineOfASignInAnAdminMutationAndAnErrorCarriesTheTierAFields(CapturedOutput output) throws Exception {
        Accounts accounts = new Accounts(jdbc, passwordEncoder);
        TotpFactors factors = new TotpFactors(jdbc, cipher, clock);
        Account admin = accounts.withRole("ADMIN");
        Account target = accounts.user();
        byte[] secret = factors.enrol(admin);
        Path auditFile = auditFile();
        long auditMark = Files.size(auditFile);
        int stdoutMark = output.getOut().length();

        CsrfSession session = factors.verified(mockMvc, SignedIn.as(mockMvc, admin), secret);
        mockMvc.perform(put("/api/admin/users/" + target.id() + "/enabled").with(session.inHeader())
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"));
        mockMvc.perform(get("/error").with(request -> {
            request.setDispatcherType(DispatcherType.ERROR);
            return request;
        }).requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/boom")
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("tier-a probe")));

        String thread = Thread.currentThread().getName();
        List<Map<String, Object>> stdout = EcsJson.rows(output.getOut().substring(stdoutMark)).stream()
                .filter(row -> thread.equals(row.get("process.thread.name"))).toList();
        List<Map<String, Object>> audit = EcsJson.rows(new String(Files.readAllBytes(auditFile),
                StandardCharsets.UTF_8).substring((int) auditMark)).stream()
                .filter(row -> thread.equals(row.get("process.thread.name"))).toList();

        assertThat(stdout).as("stdout lines").hasSizeGreaterThanOrEqualTo(5)
                .anySatisfy(row -> assertThat(row).containsEntry("log.level", "ERROR"))
                .anySatisfy(row -> assertThat(row).containsEntry("message", "Account disabled."));
        assertThat(audit).as("audit file lines").hasSizeGreaterThanOrEqualTo(3);
        for (Map<String, Object> row : stdout) {
            hasTierA(row);
        }
        for (Map<String, Object> row : audit) {
            hasTierA(row);
        }
    }

    private static void hasTierA(Map<String, Object> row) {
        assertThat(row).as("line %s", row).containsKeys(TIER_A.toArray(String[]::new))
                .containsEntry("service.name", "secured-hello-world");
        // Logback is one per JVM, configured by whichever context booted last, so the value may be another posture's.
        assertThat((String) row.get("service.environment")).isIn("dev", "production");
        assertThat((String) row.get("@timestamp")).as("ISO-8601 with an offset")
                .matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d(\\.\\d+)?(Z|[+-]\\d\\d:\\d\\d)");
        assertThat((String) row.get("service.version")).isNotBlank().doesNotContain("@");
    }

    private static Path auditFile() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        return Path.of(((RollingFileAppender<?>) context.getLogger(AuditEmitter.AUDIT_LOGGER)
                .getAppender("AUDIT_FILE")).getFile());
    }
}
