package sg.securedhello.security.login;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationConverter;

import sg.securedhello.password.PasswordPolicy;
import sg.securedhello.security.ratelimit.AuthRateLimiter;
import sg.securedhello.security.ratelimit.AuthRateLimiter.Refusal;
import sg.securedhello.security.ratelimit.RateLimit;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads {@code {"username": ..., "password": ...}} from a JSON login body. Anything else, including a body that is not
 * JSON or one past the body cap, is refused with {@link MalformedLoginException} before the authentication manager
 * runs, so it never reaches {@code matches()} and never becomes a 500 (T-AUTH-004). The parser's message, which can
 * quote the body, is dropped.
 *
 * <p>Straight after the username is read, and before anything looks it up, the per-username budget is charged
 * (ADR-010). A refusal throws {@link LoginThrottledException} here, ahead of {@code ProviderManager}: no failure event
 * is published, no account counter moves, and a real and an unknown username are refused alike (T-RL-003; T-RL-017).
 */
final class JsonCredentialsConverter implements AuthenticationConverter {

    /** The login body. Unknown members are ignored. */
    record Credentials(String username, String password) {
    }

    private final JsonMapper jsonMapper;
    private final AuthRateLimiter limiter;

    JsonCredentialsConverter(JsonMapper jsonMapper, AuthRateLimiter limiter) {
        this.jsonMapper = jsonMapper;
        this.limiter = limiter;
    }

    @Override
    public UsernamePasswordAuthenticationToken convert(HttpServletRequest request) {
        if (!isJson(request.getContentType())) {
            throw new MalformedLoginException();
        }
        Credentials credentials;
        try {
            credentials = jsonMapper.readValue(request.getInputStream(), Credentials.class);
        } catch (JacksonException | IOException ex) {
            throw new MalformedLoginException();
        }
        if (credentials == null || credentials.username() == null || credentials.password() == null) {
            throw new MalformedLoginException();
        }
        limiter.tryConsume(RateLimit.LOGIN_USERNAME, credentials.username()).ifPresent(refusal -> {
            throw new LoginThrottledException(refusal);
        });
        // Passwords are hashed in NFC, so they are verified in NFC too (ADR-002; T-CRED-006).
        return UsernamePasswordAuthenticationToken.unauthenticated(credentials.username(),
                PasswordPolicy.normalise(credentials.password()));
    }

    private static boolean isJson(String contentType) {
        try {
            return contentType != null
                    && MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType));
        } catch (InvalidMediaTypeException ex) {
            return false;
        }
    }

    /**
     * The submitted username's budget is spent: 429 {@code TOO_MANY_REQUESTS}, not the uniform 401. This branch of the
     * failure handler is deliberate; an "always 401" simplification would delete the limiter's only observable
     * behaviour (ADR-010; T-RL-002).
     */
    static final class LoginThrottledException extends AuthenticationException {

        private final transient Refusal refusal;

        LoginThrottledException(Refusal refusal) {
            super("The submitted username's login budget is spent");
            this.refusal = refusal;
        }

        Refusal refusal() {
            return refusal;
        }
    }

    /** A login body that is not the JSON credentials shape: 400 {@code VALIDATION_FAILED}, not an auth failure. */
    static final class MalformedLoginException extends AuthenticationException {

        MalformedLoginException() {
            super("The login body is not valid JSON credentials");
        }
    }
}
