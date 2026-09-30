package sg.example.helloauth.password;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param baseUrl the Have I Been Pwned range API, which the password's SHA-1 prefix is appended to
 * @param timeout how long to wait to connect, and again for the answer, before giving up
 */
@ConfigurationProperties("app.password.compromised-check")
record CompromisedPasswordCheckProperties(
        @DefaultValue("https://api.pwnedpasswords.com/range/") String baseUrl,
        @DefaultValue("3s") Duration timeout) {
}
