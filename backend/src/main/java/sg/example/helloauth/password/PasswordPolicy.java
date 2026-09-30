package sg.example.helloauth.password;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.stereotype.Component;

import sg.example.helloauth.api.ApiException;
import sg.example.helloauth.api.ErrorCode;

/**
 * The one place a candidate password is judged, wherever a password is set: registration,
 * password-reset confirm and the Bootstrap admin.
 */
@Component
public class PasswordPolicy {

    private static final Logger log = LoggerFactory.getLogger(PasswordPolicy.class);

    static final int MIN_CHARACTERS = 12;

    /** BCrypt reads only the first 72 bytes and rejects longer input. */
    static final int MAX_UTF8_BYTES = 72;

    static final String TOO_SHORT = "must be at least " + MIN_CHARACTERS + " characters";
    static final String TOO_LONG = "must be at most " + MAX_UTF8_BYTES + " bytes of UTF-8";
    static final String COMPROMISED = "must not be a password known from data breaches";

    private final CompromisedPasswordChecker compromisedPasswords;

    PasswordPolicy(CompromisedPasswordChecker compromisedPasswords) {
        this.compromisedPasswords = compromisedPasswords;
    }

    /**
     * Every rule the password breaks, as client-facing messages. Empty means it is acceptable.
     *
     * @throws ApiException 503 when the compromised-password check can't be reached: no password
     *         is accepted unchecked
     */
    public List<String> violations(String password) {
        List<String> violations = new ArrayList<>();
        if (password.codePointCount(0, password.length()) < MIN_CHARACTERS) {
            violations.add(TOO_SHORT);
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            violations.add(TOO_LONG);
        }
        if (isCompromised(password)) {
            violations.add(COMPROMISED);
        }
        return violations;
    }

    private boolean isCompromised(String password) {
        try {
            return compromisedPasswords.check(password).isCompromised();
        } catch (CompromisedPasswordCheckUnavailableException ex) {
            log.warn("Compromised-password check unavailable", ex);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE,
                    "Passwords can't be checked right now. Please try again later.");
        }
    }
}
