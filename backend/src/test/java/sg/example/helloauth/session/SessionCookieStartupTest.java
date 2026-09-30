package sg.example.helloauth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import sg.example.helloauth.HelloAuthApplication;
import sg.example.helloauth.support.OfflineCompromisedPasswords;

/** Starts the real application, because the rule is about whether it may start at all. */
class SessionCookieStartupTest {

    private static ConfigurableApplicationContext start(String... args) {
        String[] common = {"--server.port=0", "--spring.datasource.url=jdbc:h2:mem:cookie-startup"};
        // Startup creates the Bootstrap admin, whose password is checked offline.
        return new SpringApplicationBuilder(HelloAuthApplication.class, OfflineCompromisedPasswords.class)
                .run(Stream.concat(Stream.of(common), Stream.of(args)).toArray(String[]::new));
    }

    @Test
    void startupFailsWhenTheSecureFlagIsTurnedOffOutsideTheDevProfile() {
        assertThatThrownBy(() -> start("--app.session.secure-cookie=false").close())
                .rootCause()
                .hasMessage("app.session.secure-cookie may be false only in the dev profile");
    }

    @Test
    void devProfileMayTurnTheSecureFlagOff() {
        try (ConfigurableApplicationContext context = start("--spring.profiles.active=dev")) {
            assertThat(context.isRunning()).isTrue();
        }
    }
}
