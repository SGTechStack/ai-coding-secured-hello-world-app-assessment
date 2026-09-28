package com.example.helloauth.web;

import com.example.helloauth.web.dto.ApiPayloads.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * The 401 for an unauthenticated request, as story 5 requires.
 *
 * <p>This exists because authentication failures happen in the filter chain, before any controller,
 * so {@link ApiExceptionHandler} never sees them. Left to its defaults Spring Security would answer
 * with a redirect to an HTML login page, which a JSON client reports as a parse error rather than as
 * "you are not logged in".
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        ErrorResponseWriter.write(
                objectMapper,
                response,
                HttpStatus.UNAUTHORIZED,
                ApiError.of("unauthenticated", "You need to log in to do that."));
    }
}
