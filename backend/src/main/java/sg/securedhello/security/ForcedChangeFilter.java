package sg.securedhello.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;
import sg.securedhello.user.SignedInUser;

/**
 * Confines a session whose credential must be changed (ADR-046; ADR-047) to the <em>forced-change allowlist</em>, which
 * holds exactly five routes: {@code POST /api/login}, {@code GET /api/csrf}, {@code GET /api/profile},
 * {@code POST /api/logout} and {@code PATCH /api/profile/password}. Every other request from such a session, including
 * {@code /api/mfa/**}, {@code /api/hello} and a route with no matrix row, gets 403 {@code PASSWORD_CHANGE_REQUIRED}
 * (ADR-023; R-STD-029; REJ-055).
 *
 * <p>It sits just before authorization, so CSRF and the session checks have already run, and it reads only the
 * session's principal: the flag is taken from the account at sign-in and cleared on the session when the change
 * completes. Anonymous requests pass through to the matrix.
 */
final class ForcedChangeFilter extends OncePerRequestFilter {

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

    /** The forced-change allowlist: exactly five routes (spec, API surface). */
    static final List<RequestMatcher> ALLOWLIST = List.of(
            PATHS.matcher(HttpMethod.POST, "/api/login"),
            PATHS.matcher(HttpMethod.GET, "/api/csrf"),
            PATHS.matcher(HttpMethod.GET, "/api/profile"),
            PATHS.matcher(HttpMethod.POST, "/api/logout"),
            PATHS.matcher(HttpMethod.PATCH, "/api/profile/password"));

    private final ProblemDetailWriter writer;

    ForcedChangeFilter(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SignedInUser.current().filter(SignedInUser::passwordChangeRequired).isPresent()
                && ALLOWLIST.stream().noneMatch(route -> route.matches(request))) {
            writer.write(request, response, ErrorCode.PASSWORD_CHANGE_REQUIRED);
            return;
        }
        chain.doFilter(request, response);
    }
}
