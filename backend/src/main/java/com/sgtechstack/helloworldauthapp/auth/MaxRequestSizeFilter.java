package com.sgtechstack.helloworldauthapp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects oversized request bodies before anything reads them.
 *
 * <p>Tomcat's {@code max-http-form-post-size} only applies to form-encoded
 * bodies, and every endpoint on this API except login takes JSON, for which
 * Spring Boot applies no default cap at all. Bean validation does not help
 * either: {@code @Size} constraints are checked after Jackson has already
 * buffered and parsed the whole payload, so an arbitrarily large body is fully
 * materialised before anything rejects it. That is cheap for the caller and
 * expensive for the server, and it lands on endpoints reachable without
 * authentication.
 *
 * <p>Checking {@code Content-Length} up front is the earliest point at which
 * this can be refused. A caller using chunked encoding sends no
 * {@code Content-Length}, so this is a floor rather than a guarantee — a hard
 * ceiling belongs at the ingress or reverse proxy, which can enforce it while
 * streaming. Recorded as such rather than overstated.
 */
public class MaxRequestSizeFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MaxRequestSizeFilter.class);
    private static final String TOO_LARGE_MESSAGE = "Request body too large.";

    private final long maxBytes;
    private final ObjectMapper objectMapper;

    public MaxRequestSizeFilter(long maxBytes, ObjectMapper objectMapper) {
        this.maxBytes = maxBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long declaredLength = request.getContentLengthLong();

        if (declaredLength > maxBytes) {
            log.info("Rejected oversized request uri={} declaredBytes={} maxBytes={}",
                    request.getRequestURI(), declaredLength, maxBytes);
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(TOO_LARGE_MESSAGE)));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
