package com.example.helloauth.service;

import com.example.helloauth.domain.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * Creating and destroying server-side sessions.
 *
 * <p>This is the only service that touches the servlet API, and it is deliberately the only one:
 * starting a session is inseparable from the request and response it happens on, so rather than
 * spread that coupling across the services that need sessions, it is concentrated here and the rest
 * of the service layer stays transport-free.
 *
 * <p>Being able to end <em>somebody else's</em> sessions — which password reset and the admin
 * mutations both need — is why the PRD specifies Spring Session rather than a plain servlet session.
 * Spring Session indexes sessions by principal name, so they can be found and deleted without a
 * reference to the request that created them.
 */
@Service
public class SessionService {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final SecurityContextRepository securityContextRepository;

    public SessionService(
            FindByIndexNameSessionRepository<? extends Session> sessionRepository,
            SecurityContextRepository securityContextRepository) {
        this.sessionRepository = sessionRepository;
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * Starts an authenticated session for this request.
     *
     * <p>Any pre-existing session is destroyed rather than reused. That is session-fixation
     * protection: without it, an attacker who can plant a session identifier in a victim's browser
     * before login ends up holding a valid authenticated session afterwards.
     *
     * <p>Note what is <em>not</em> stored: no password, and no snapshot of the account beyond the
     * username and role. Authorities are resolved at login and cached in the session, which is why
     * disabling an account or changing its role also ends that account's sessions — otherwise the
     * cached authority would outlive the decision.
     */
    public void startSession(
            String username, Role role, HttpServletRequest request, HttpServletResponse response) {
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        request.getSession(true);

        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        username, null, List.of(new SimpleGrantedAuthority(role.authority())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    /**
     * Destroys every session belonging to one account, wherever it was created.
     *
     * @return how many sessions were ended, which is worth auditing
     */
    public int endAllSessionsFor(String username) {
        Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(username);
        sessions.keySet().forEach(sessionRepository::deleteById);
        return sessions.size();
    }
}
