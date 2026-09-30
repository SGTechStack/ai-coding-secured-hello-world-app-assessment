package sg.example.helloauth.session;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param secureCookie whether the session cookie is {@code Secure}; only the dev profile may set false
 * @param idleTimeout how long a logged-in session may go unused
 * @param absoluteTimeout how long a logged-in session may last, however active
 */
@ConfigurationProperties("app.session")
record SessionProperties(
        @DefaultValue("true") boolean secureCookie,
        @DefaultValue("15m") Duration idleTimeout,
        @DefaultValue("8h") Duration absoluteTimeout) {
}
