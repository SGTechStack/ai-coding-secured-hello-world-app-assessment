package com.example.auth.security;

import com.example.auth.auth.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Parses {@code POST /api/auth/login}'s JSON body ({@code
 * {"username","password"}}) instead of form-urlencoded parameters, keeping
 * the frontend's existing request shape unchanged. Registered in place of
 * the default {@link UsernamePasswordAuthenticationFilter} via {@code
 * http.addFilterAt(...)}.
 */
public class JsonUsernamePasswordAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private static final String PARSED_BODY_ATTRIBUTE =
            JsonUsernamePasswordAuthenticationFilter.class.getName() + ".PARSED_BODY";

    private final ObjectMapper objectMapper;

    public JsonUsernamePasswordAuthenticationFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // loginProcessingUrl-equivalent: restrict this filter to POST /api/auth/login.
        setFilterProcessesUrl("/api/auth/login");
    }

    @Override
    protected String obtainUsername(HttpServletRequest request) {
        return parseBody(request).username();
    }

    @Override
    protected String obtainPassword(HttpServletRequest request) {
        return parseBody(request).password();
    }

    private LoginRequest parseBody(HttpServletRequest request) {
        LoginRequest cached = (LoginRequest) request.getAttribute(PARSED_BODY_ATTRIBUTE);
        if (cached != null) {
            return cached;
        }

        LoginRequest parsed;
        try {
            parsed = objectMapper.readValue(request.getInputStream(), LoginRequest.class);
        } catch (JacksonException | java.io.IOException ex) {
            // Malformed/empty body: fall through with blank credentials so
            // authentication fails the normal (generic) way rather than 500ing.
            parsed = new LoginRequest(null, null);
        }
        request.setAttribute(PARSED_BODY_ATTRIBUTE, parsed);
        return parsed;
    }
}
