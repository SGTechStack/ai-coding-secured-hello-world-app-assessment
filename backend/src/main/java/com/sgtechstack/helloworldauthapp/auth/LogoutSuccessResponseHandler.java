package com.sgtechstack.helloworldauthapp.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Responds to a successful logout with a plain 204. The session is already
 * invalidated and the cookie already cleared by Spring Security's logout
 * filter before this handler runs; a JSON API has no use for the default
 * redirect-to-login behaviour.
 */
@Component
public class LogoutSuccessResponseHandler implements LogoutSuccessHandler {

    @Override
    public void onLogoutSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
