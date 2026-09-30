package sg.example.helloauth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import sg.example.helloauth.HelloAuthApplication;
import sg.example.helloauth.support.OfflineCompromisedPasswords;

/** Starts the real application, because the H2 console is a servlet of its own, outside MockMvc. */
class H2ConsoleTest {

    private static ConfigurableApplicationContext start(String... args) {
        String[] common = {"--server.port=0", "--spring.datasource.url=jdbc:h2:mem:h2-console"};
        // Startup creates the Bootstrap admin, whose password is checked offline.
        return new SpringApplicationBuilder(HelloAuthApplication.class, OfflineCompromisedPasswords.class)
                .run(Stream.concat(Stream.of(common), Stream.of(args)).toArray(String[]::new));
    }

    private static HttpResponse<String> get(ConfigurableApplicationContext app, String path) throws Exception {
        URI uri = URI.create("http://localhost:" + app.getEnvironment().getProperty("local.server.port") + path);
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void devProfileServesTheConsoleThroughItsOwnFilterChain() throws Exception {
        try (ConfigurableApplicationContext app = start("--spring.profiles.active=dev")) {
            HttpResponse<String> console = get(app, "/h2-console/");

            assertThat(console.statusCode()).isEqualTo(200);
            assertThat(console.body()).contains("H2 Console");
            // The console uses frames, which the API's chain denies.
            assertThat(console.headers().firstValue("X-Frame-Options")).hasValue("SAMEORIGIN");
        }
    }

    @Test
    void outsideTheDevProfileTheConsoleDoesNotExist() throws Exception {
        try (ConfigurableApplicationContext app = start()) {
            assertThat(app.containsBean("h2Console")).isFalse();
            assertThat(get(app, "/h2-console/").statusCode()).isEqualTo(401);
        }
    }

    @Test
    void startupFailsWhenTheConsoleIsEnabledOutsideTheDevProfile() {
        assertThatThrownBy(() -> start("--spring.h2.console.enabled=true").close())
                .rootCause()
                .hasMessage("spring.h2.console.enabled may be true only in the dev profile");
    }
}
