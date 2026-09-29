package sg.securedhello.security.login;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationConverter;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads {@code {"username": ..., "password": ...}} from a JSON login body. Anything else, including a body that is not
 * JSON, is refused with {@link MalformedLoginException} before the authentication manager runs, so it never reaches
 * {@code matches()} and never becomes a 500 (T-AUTH-004). The parser's message, which can quote the body, is dropped.
 */
final class JsonCredentialsConverter implements AuthenticationConverter {

    /** The login body. Unknown members are ignored. */
    record Credentials(String username, String password) {
    }

    private final JsonMapper jsonMapper;

    JsonCredentialsConverter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
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
        return UsernamePasswordAuthenticationToken.unauthenticated(credentials.username(), credentials.password());
    }

    private static boolean isJson(String contentType) {
        try {
            return contentType != null
                    && MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType));
        } catch (InvalidMediaTypeException ex) {
            return false;
        }
    }

    /** A login body that is not the JSON credentials shape: 400 {@code VALIDATION_FAILED}, not an auth failure. */
    static final class MalformedLoginException extends AuthenticationException {

        MalformedLoginException() {
            super("The login body is not valid JSON credentials");
        }
    }
}
