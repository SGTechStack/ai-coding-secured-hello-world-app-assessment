package com.example.helloauth.web;

import com.example.helloauth.web.dto.ApiPayloads.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/**
 * The 403 for an authenticated-but-not-permitted request.
 *
 * <p>Covers story 8's "a USER calling an admin endpoint gets 403" and also every rejected CSRF token,
 * because {@link CsrfException} is an {@link AccessDeniedException} and both arrive here. The error
 * codes differ so a client can tell "you are not allowed" from "your token is stale, fetch a new
 * one" — two situations with completely different remedies.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException deniedException)
            throws IOException {
        ApiError error =
                deniedException instanceof CsrfException
                        ? ApiError.of(
                                "invalid_csrf_token",
                                "Your security token was missing or stale. Reload and try again.")
                        : ApiError.of("forbidden", "You do not have permission to do that.");
        ErrorResponseWriter.write(objectMapper, response, HttpStatus.FORBIDDEN, error);
    }
}
