package sg.securedhello.e2e;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.securedhello.SecuredHelloApplication;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.TemporaryH2FileInitializer;

/**
 * The backend the Playwright suite signs in against (level E). It lives in the test sources, so nothing here can
 * reach a production build: the real application under the {@code dev} profile, on a fresh temporary H2 file, with the
 * test-only {@code TestSecrets}, plus the fixture accounts the browser tests use, since registration does not exist
 * yet. Started by {@code frontend/playwright.config.ts}:
 *
 * <pre>mvn -f backend/pom.xml test-compile spring-boot:test-run
 *     -Dspring-boot.run.main-class=sg.securedhello.e2e.E2eBackend</pre>
 *
 * Its defaults (port 18010, SPA origin {@code http://localhost:15110}) can be overridden with {@code --name=value}
 * arguments.
 */
public final class E2eBackend {

    /**
     * One fixture account per browser and test, so parallel tests never displace each other's session (one session per
     * account, the new login wins). Mirrored in {@code frontend/e2e/fixtures.ts}.
     */
    static final List<String> USERNAMES = List.of("chromium", "firefox").stream()
            .flatMap(browser -> List.of("hello", "service-worker").stream().map(test -> "e2e-" + browser + "-" + test))
            .toList();
    static final String PASSWORD = "e2e-password-correct-horse";

    private static final String COMMAND_LINE = CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME;

    private E2eBackend() {
    }

    public static void main(String[] args) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("server.port", "18010");
        defaults.put("app.origins.spa", "http://localhost:15110");
        defaults.put("app.origins.api", "http://localhost:18010");
        new SpringApplicationBuilder(SecuredHelloApplication.class, Fixtures.class)
                .profiles("dev")
                .initializers(new TemporaryH2FileInitializer(), context -> {
                    // Above the configuration files, below the command line, which overrides any of them.
                    MutablePropertySources sources = context.getEnvironment().getPropertySources();
                    MapPropertySource e2e = new MapPropertySource("e2eDefaults", defaults);
                    if (sources.contains(COMMAND_LINE)) {
                        sources.addAfter(COMMAND_LINE, e2e);
                    } else {
                        sources.addFirst(e2e);
                    }
                })
                .run(args);
    }

    /** Creates the fixture accounts once the context is ready. */
    @Configuration(proxyBeanMethods = false)
    static class Fixtures {

        @Bean
        ApplicationRunner e2eAccounts(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
            return arguments -> USERNAMES.forEach(name -> new Accounts(jdbc, passwordEncoder).named(name, PASSWORD));
        }
    }
}
