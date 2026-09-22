package com.sgtechstack.helloworldauthapp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * By default, when {@code ConcurrentSessionFilter} finds a session that
 * was expired via {@code SessionRegistry} (e.g. by a password reset), it
 * responds with 200 and a human-readable "this session has been expired"
 * message — fine for a page reload, wrong for a JSON API, where a stale
 * session should look exactly like any other unauthenticated request:
 * 401, same {@link ErrorResponse} shape as {@link RestAuthenticationEntryPoint}.
 */
@Component
public class RestSessionExpiredStrategy implements SessionInformationExpiredStrategy {

    private final ObjectMapper objectMapper;

    public RestSessionExpiredStrategy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onExpiredSessionDetected(SessionInformationExpiredEvent event) throws IOException {
        var response = event.getResponse();
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of("Authentication required")));
    }
}
