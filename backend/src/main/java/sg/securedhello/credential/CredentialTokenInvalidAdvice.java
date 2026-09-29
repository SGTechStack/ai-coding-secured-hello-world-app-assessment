package sg.securedhello.credential;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;

/**
 * Writes a {@link CredentialTokenInvalidException} as 400 {@code RESET_TOKEN_INVALID}, for every endpoint that redeems
 * a credential token. Ordered first, so the catch-all {@code ProblemExceptionHandler} never sees it.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CredentialTokenInvalidAdvice {

    private final ProblemDetailWriter writer;

    public CredentialTokenInvalidAdvice(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @ExceptionHandler(CredentialTokenInvalidException.class)
    void invalid(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writer.write(request, response, ErrorCode.RESET_TOKEN_INVALID);
    }
}
