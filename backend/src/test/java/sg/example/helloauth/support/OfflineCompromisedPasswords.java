package sg.example.helloauth.support;

import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.security.authentication.password.CompromisedPasswordDecision;

/**
 * Stands in for the Have I Been Pwned checker, so tests never call the network. It knows only
 * {@link #COMPROMISED}, each of which the real API also reports as breached. A test that sets
 * {@link #PROPERTY} to {@code real} gets the real checker instead, to point at a stub.
 */
@TestConfiguration(proxyBeanMethods = false)
public class OfflineCompromisedPasswords {

    public static final String PROPERTY = "test.compromised-passwords";

    public static final Set<String> COMPROMISED = Set.of("password", "passwordpassword");

    @Bean
    @Primary
    @ConditionalOnProperty(name = PROPERTY, havingValue = "offline", matchIfMissing = true)
    CompromisedPasswordChecker offlineCompromisedPasswordChecker() {
        return password -> new CompromisedPasswordDecision(COMPROMISED.contains(password));
    }
}
