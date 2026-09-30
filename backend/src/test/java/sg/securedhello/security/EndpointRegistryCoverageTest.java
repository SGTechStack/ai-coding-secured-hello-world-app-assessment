package sg.securedhello.security;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import sg.securedhello.audit.AuditEvent;
import sg.securedhello.credential.CredentialTokenType;
import sg.securedhello.security.AuthorizationMatrix.Route;
import sg.securedhello.security.ratelimit.RateLimit;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;

/**
 * Every {@code @RequestMapping} handler has a disposition in all three registries (TM-05; TM-01; ADR-018; R-BLD-016):
 * the authorization matrix (a role row or the whitelist), the budget table (its own row, or the stated default:
 * every route is metered by the session-miss budget and an unlisted route is bounded at the edge; ADR-017; R-OPS-006)
 * and the audit catalogue (its rows, or an omission recorded here with its reason). A handler added without a line in
 * {@link #AUDIT} fails this test until someone decides its audit disposition.
 */
class EndpointRegistryCoverageTest extends CtxDefaultTest {

    /** The one handler outside {@code /api}: the envelope's error dispatch, reached only on an ERROR dispatch. */
    private static final String ERROR_DISPATCH = "* /error";

    /**
     * The handlers with no budget row of their own, covered by the stated default instead: the session-miss budget
     * meters every request (ADR-017) and a route absent from the table is bounded at the edge (R-OPS-006). Every one
     * needs a signed-in session, and the admin surface also the factor, so none is an anonymous password oracle.
     */
    private static final Set<String> DEFAULT_BUDGET = Set.of("GET /api/profile", "GET /api/hello",
            "GET /api/admin/users", "GET /api/admin/users/{id}", "POST /api/admin/users",
            "PUT /api/admin/users/{id}/enabled", "PUT /api/admin/users/{id}/role", "DELETE /api/admin/users/{id}",
            "POST /api/admin/users/{id}/password-reset", "POST /api/admin/users/{id}/unlock",
            "DELETE /api/admin/users/{id}/totp", ERROR_DISPATCH);

    /**
     * Sign-in and sign-out, answered by security filters (SignIn), so they have no handler; the login budget row and
     * their audit rows (LOGIN_*, SESSION_START, LOGOUT) are theirs.
     */
    private static final Set<String> FILTER_ROUTES = Set.of("POST /api/login", "POST /api/logout");

    /** A handler's audit disposition: the rows it writes, or why it writes none. */
    private record AuditDisposition(Set<AuditEvent> rows, String omission) {

        static AuditDisposition rows(AuditEvent... rows) {
            return new AuditDisposition(EnumSet.copyOf(Arrays.asList(rows)), "");
        }

        static AuditDisposition omitted(String reason) {
            return new AuditDisposition(Set.of(), reason);
        }
    }

    /** The audit registry for every handler, keyed "METHOD path" as the matrix writes it. */
    private static final Map<String, AuditDisposition> AUDIT = Map.ofEntries(
            entry("GET /api/csrf", AuditDisposition.rows(AuditEvent.SOURCE_THROTTLED, AuditEvent.SHED_EPISODE_CLEARED)),
            entry("POST /api/register", AuditDisposition.rows(AuditEvent.REGISTRATION_ACCEPTED,
                    AuditEvent.REGISTRATION_REFUSED, AuditEvent.PENDING_REGISTRATION_LAPSED)),
            entry("POST /api/register/activate", AuditDisposition.rows(AuditEvent.CREDENTIAL_TOKEN_REFUSED)),
            entry("POST /api/password-reset/request", AuditDisposition.rows(AuditEvent.PASSWORD_RESET_REQUESTED,
                    AuditEvent.IDENTIFIER_THROTTLED)),
            entry("POST /api/password-reset/confirm", AuditDisposition.rows(AuditEvent.PASSWORD_RESET_COMPLETED,
                    AuditEvent.LOCKOUT_CLEARED, AuditEvent.CREDENTIAL_TOKEN_REFUSED)),
            entry("GET /api/profile", AuditDisposition.rows(AuditEvent.PROFILE_READ)),
            entry("GET /api/hello", AuditDisposition.omitted("the greeting changes nothing")),
            entry("PATCH /api/profile/password", AuditDisposition.omitted("a self-service change writes no row of "
                    + "its own; owner notification is out of scope (ADR-008)")),
            entry("POST /api/mfa/totp/enrolment", AuditDisposition.rows(AuditEvent.TOTP_ENROLMENT_PROVISIONED)),
            entry("POST /api/mfa/totp/enrolment/confirmation", AuditDisposition.rows(
                    AuditEvent.TOTP_ENROLMENT_CONFIRMED, AuditEvent.TOTP_ENROLMENT_FAILED)),
            entry("POST /api/mfa/totp/verification", AuditDisposition.rows(AuditEvent.TOTP_VERIFIED,
                    AuditEvent.TOTP_VERIFICATION_FAILED, AuditEvent.TOTP_FACTOR_LOCKED,
                    AuditEvent.TOTP_FACTOR_DISABLED)),
            entry("GET /api/admin/users", AuditDisposition.rows(AuditEvent.ADMIN_USERS_LISTED)),
            entry("GET /api/admin/users/{id}", AuditDisposition.rows(AuditEvent.ADMIN_USER_VIEWED)),
            entry("POST /api/admin/users", AuditDisposition.rows(AuditEvent.ADMIN_USER_INVITED,
                    AuditEvent.ADMIN_USER_REINVITED)),
            entry("PUT /api/admin/users/{id}/enabled", AuditDisposition.rows(AuditEvent.ADMIN_USER_ENABLED,
                    AuditEvent.ADMIN_USER_DISABLED, AuditEvent.ADMIN_ACTION_REFUSED)),
            entry("PUT /api/admin/users/{id}/role", AuditDisposition.rows(AuditEvent.ADMIN_USER_PROMOTED,
                    AuditEvent.ADMIN_USER_DEMOTED, AuditEvent.ADMIN_ACTION_REFUSED)),
            entry("DELETE /api/admin/users/{id}", AuditDisposition.rows(AuditEvent.ADMIN_USER_DELETED,
                    AuditEvent.ADMIN_ACTION_REFUSED)),
            entry("POST /api/admin/users/{id}/password-reset", AuditDisposition.rows(AuditEvent.ADMIN_RESET_ISSUED)),
            entry("POST /api/admin/users/{id}/unlock", AuditDisposition.rows(AuditEvent.ADMIN_USER_UNLOCKED,
                    AuditEvent.ADMIN_ACTION_REFUSED)),
            entry("DELETE /api/admin/users/{id}/totp", AuditDisposition.rows(AuditEvent.TOTP_REMOVED,
                    AuditEvent.ADMIN_ACTION_REFUSED)),
            entry(ERROR_DISPATCH, AuditDisposition.omitted("renders the envelope for a request that already failed; "
                    + "whatever refused it wrote that request's row")));

    @Autowired
    private AuthorizationMatrix matrix;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    /** Every handler, as "METHOD path"; a handler mapped without a method is "* path". */
    private Set<String> handlers() {
        Set<String> handlers = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                if (methods.isEmpty()) {
                    handlers.add("* " + pattern);
                }
                methods.forEach(method -> handlers.add(method.name() + " " + pattern));
            }
        }
        return handlers;
    }

    private static String key(HttpMethod method, String path) {
        return method.name() + " " + path;
    }

    @Test
    @Proves("T-ARCH-005")
    void everyHandlerHasAMatrixABudgetAndAnAuditDisposition() {
        Set<String> handlers = handlers();
        assertThat(handlers).contains(ERROR_DISPATCH).hasSizeGreaterThan(20);

        Set<String> matrixRoutes = matrix.whitelist().stream().map(route -> key(route.method(), route.path()))
                .collect(Collectors.toCollection(TreeSet::new));
        matrix.rolesByRoute().keySet().forEach((Route route) -> matrixRoutes.add(key(route.method(), route.path())));
        Set<String> budgeted = Arrays.stream(RateLimit.values()).map(row -> key(row.method(), row.path()))
                .collect(Collectors.toSet());

        for (String handler : handlers) {
            if (!handler.equals(ERROR_DISPATCH)) {
                assertThat(matrixRoutes).as("matrix row or whitelist for %s", handler).contains(handler);
            }
            assertThat(budgeted.contains(handler) != DEFAULT_BUDGET.contains(handler))
                    .as("%s has its own budget row or is listed under the stated default, not both", handler).isTrue();
            assertThat(AUDIT).as("audit disposition for %s", handler).containsKey(handler);
            AuditDisposition audit = AUDIT.get(handler);
            assertThat(audit.rows().isEmpty() != audit.omission().isEmpty())
                    .as("%s names rows or an omission reason, not both", handler).isTrue();
        }
        assertThat(AUDIT.keySet()).as("dispositions for handlers that no longer exist").isSubsetOf(handlers);
        Set<String> routes = new TreeSet<>(handlers);
        routes.addAll(FILTER_ROUTES);
        assertThat(budgeted).as("budget rows for routes that no longer exist").isSubsetOf(routes);
        assertThat(DEFAULT_BUDGET).as("default-budget entries for routes that no longer exist").isSubsetOf(handlers);
    }

    @Test
    @Proves("T-CRED-023")
    void noHandlerIssuesOrRedeemsARecoveryCodeAndNoRegistryHasARecoveryEntry() {
        assertThat(EnumSet.allOf(CredentialTokenType.class))
                .containsExactlyInAnyOrder(CredentialTokenType.ACTIVATION, CredentialTokenType.PASSWORD_RESET);
        assertThat(handlers()).noneMatch(handler -> handler.toLowerCase().contains("recovery"));
        assertThat(handlerMapping.getHandlerMethods().values())
                .noneMatch(method -> method.getMethod().getName().toLowerCase().contains("recovery"));
        assertThat(AUDIT.keySet()).noneMatch(handler -> handler.toLowerCase().contains("recovery"));
        assertThat(matrix.whitelist()).noneMatch(route -> route.path().toLowerCase().contains("recovery"));
        assertThat(matrix.rolesByRoute().keySet()).noneMatch(route -> route.path().toLowerCase().contains("recovery"));
    }
}
