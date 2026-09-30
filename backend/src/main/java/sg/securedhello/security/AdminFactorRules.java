package sg.securedhello.security;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AllRequiredFactorsAuthorizationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import sg.securedhello.mfa.TotpFactorGrant;
import sg.securedhello.security.AuthorizationMatrix.Route;

/**
 * The admin factor rules, hand-composed per matrix row, role first (ADR-026). Every row on {@code /api/admin/**} gets
 * its role check, from the matrix, followed by a factor check, joined by the two-argument
 * {@code AuthorizationManagers.allOf(AuthorizationDecision, ...)} whose all-abstain default is deny. So a non-admin
 * gets 403 and never a factor answer, and an anonymous caller gets 401. No {@code @EnableMultiFactorAuthentication},
 * no factor-bearing factory and no {@code RoleHierarchy}.
 *
 * <p>The factor check requires {@code FACTOR_PASSWORD} and {@code FACTOR_TOTP}, both as {@code FactorGrantedAuthority}
 * instances issued within the rule's duration, on the shared {@link Clock} (ADR-021):
 * <ul>
 *   <li><b>Reads</b> ({@code GET}): the absolute session lifetime. Not a time bound: the factor is always granted
 *       after the session's {@code AUTH_INSTANT}, so it outlives the session. It makes a plain authority carrying the
 *       factor's name, as a degraded session round trip could leave, fail closed.</li>
 *   <li><b>Mutations</b> (every other method, {@code HEAD} included, which so fails closed): {@code FACTOR_TOTP}
 *       issued within 10 minutes.</li>
 * </ul>
 */
final class AdminFactorRules {

    /** How recently a mutation's {@code FACTOR_TOTP} must have been issued (ADR-021). */
    static final Duration MUTATION_FACTOR_VALIDITY = Duration.ofMinutes(10);

    private static final String ADMIN_SURFACE = "/api/admin";

    private final Duration readValidity;
    private final Duration sessionLifetime;
    private final Clock clock;

    /** The rules the application runs: reads accept a factor for the whole absolute session lifetime (ADR-021). */
    AdminFactorRules(Duration sessionLifetime, Clock clock) {
        this(sessionLifetime, sessionLifetime, clock);
    }

    /**
     * @throws IllegalStateException if {@code readValidity} is shorter than {@code sessionLifetime}: admins would be
     *         sent back to the challenge mid-session with no code change to blame (ADR-021; T-CFG-019)
     */
    AdminFactorRules(Duration readValidity, Duration sessionLifetime, Clock clock) {
        if (readValidity.compareTo(sessionLifetime) < 0) {
            throw new IllegalStateException("Startup refused: the admin read rule's factor validity " + readValidity
                    + " is shorter than the absolute session lifetime " + sessionLifetime + " (ADR-021)");
        }
        this.readValidity = readValidity;
        this.sessionLifetime = sessionLifetime;
        this.clock = clock;
    }

    /** Whether {@code route} is on the admin surface, {@code /api/admin/**}, which requires the factor. */
    private static boolean covers(Route route) {
        return route.path().equals(ADMIN_SURFACE) || route.path().startsWith(ADMIN_SURFACE + "/");
    }

    /**
     * Refuses startup unless the matrix gives the admin surface both rule sets, at least one read ({@code GET}) row and
     * one mutation row (ADR-026; T-CFG-018). Either set empty would leave that half of the surface to the final
     * {@code denyAll()} with no configuration error to show for it.
     */
    static void requireBothRuleSets(Collection<Route> routes) {
        boolean reads = routes.stream().anyMatch(route -> covers(route) && HttpMethod.GET.equals(route.method()));
        boolean mutations = routes.stream().anyMatch(route -> covers(route) && !HttpMethod.GET.equals(route.method()));
        if (!reads || !mutations) {
            throw new IllegalStateException("Startup refused: the admin factor rules need at least one read and one "
                    + "mutation row on " + ADMIN_SURFACE + "/** (ADR-026); reads=" + reads
                    + ", mutations=" + mutations);
        }
    }

    /**
     * The rule for a matrix {@code route}: any of {@code roles}, and on the admin surface both factors after the
     * role. The one place that decides which routes need the factor.
     */
    AuthorizationManager<RequestAuthorizationContext> rule(Route route, List<String> roles) {
        AuthorityAuthorizationManager<RequestAuthorizationContext> role =
                AuthorityAuthorizationManager.hasAnyRole(roles.toArray(String[]::new));
        if (!covers(route)) {
            return role;
        }
        Duration totpValidity = HttpMethod.GET.equals(route.method()) ? readValidity : MUTATION_FACTOR_VALIDITY;
        AllRequiredFactorsAuthorizationManager<RequestAuthorizationContext> factors =
                AllRequiredFactorsAuthorizationManager.<RequestAuthorizationContext>builder()
                        .requireFactor(factor -> factor.passwordAuthority().validDuration(sessionLifetime))
                        .requireFactor(factor -> factor.authority(TotpFactorGrant.AUTHORITY)
                                .validDuration(totpValidity))
                        .build();
        factors.setClock(clock);
        return AuthorizationManagers.allOf(new AuthorizationDecision(false), role, factors);
    }
}
