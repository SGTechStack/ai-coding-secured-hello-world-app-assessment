package sg.securedhello.password;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.nulabinc.zxcvbn.Zxcvbn;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import sg.securedhello.security.PasswordProperties;

/**
 * Builds the {@link PasswordPolicy} from configuration and the two pinned lists on the classpath: the breach slice and
 * the context word list (REJ-004; R-CRED-006). The estimator is zxcvbn4j, pinned at 1.9.0 (ADR-005).
 */
@Configuration(proxyBeanMethods = false)
public class PasswordPolicyConfig {

    static final String BREACH_SLICE = "password/breach-slice.txt";
    static final String CONTEXT_WORDS = "password/context-words.txt";

    @Bean
    PasswordPolicy passwordPolicy(PasswordProperties properties,
            @Value("${spring.application.name}") String serviceName) {
        Zxcvbn zxcvbn = new Zxcvbn();
        return new PasswordPolicy(
                new PasswordPolicy.Limits(properties.minLength(), properties.maxBytes(),
                        properties.minStrengthScore()),
                entries(BREACH_SLICE), entries(CONTEXT_WORDS), serviceName,
                (password, userInputs) -> zxcvbn.measure(password, userInputs).getScore());
    }

    /** The non-blank lines of a list that do not start with {@code #}. */
    static List<String> entries(String location) {
        try {
            return new ClassPathResource(location).getContentAsString(StandardCharsets.UTF_8).lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read the password list " + location, ex);
        }
    }
}
