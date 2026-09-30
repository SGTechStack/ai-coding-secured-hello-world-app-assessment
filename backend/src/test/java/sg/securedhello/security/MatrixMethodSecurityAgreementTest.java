package sg.securedhello.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import sg.securedhello.mfa.TotpFactorGrant;
import sg.securedhello.security.AuthorizationMatrix.Route;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;

/**
 * For every role-guarded entry of the bound authorization matrix, the request chain's decision and the
 * {@code @PreAuthorize} role check on the service method the route reaches agree (ADR-043; ADR-026; R-MFA-003). The
 * chain is asked directly, for each role holding both factors issued now, so only the role decides. The annotation is
 * read from the service methods each handler calls. Every {@code /api/admin/**} route must reach one, since there the
 * role gate has two layers; elsewhere the matrix row is the only role gate, and no annotation may contradict it.
 */
class MatrixMethodSecurityAgreementTest extends CtxDefaultTest {

    private static final List<String> ROLES = List.of("USER", "ADMIN");

    private static final Pattern ROLE_EXPRESSION = Pattern.compile("has(?:Any)?Role\\(([^)]*)\\)");

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("sg.securedhello");

    @Autowired
    private AuthorizationMatrix matrix;

    @Autowired
    private FilterChainProxy filterChainProxy;

    @Autowired
    private WebApplicationContext web;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @Proves("T-ADM-012")
    void theChainAndMethodSecurityAgreeOnEveryMatrixEntry() throws Exception {
        AuthorizationManager<HttpServletRequest> chain = chainAuthorization();
        Map<Route, List<String>> guarded = matrix.rolesByRoute();
        assertThat(guarded).isNotEmpty();
        int annotated = 0;

        for (Map.Entry<Route, List<String>> entry : guarded.entrySet()) {
            Route route = entry.getKey();
            Set<String> chainRoles = new TreeSet<>();
            for (String role : ROLES) {
                if (chain.authorize(() -> holding(role), request(route)).isGranted()) {
                    chainRoles.add(role);
                }
            }
            assertThat(chainRoles).as("chain decision for %s", route).isEqualTo(new TreeSet<>(entry.getValue()));

            Set<Set<String>> annotations = preAuthorizeRoles(handlerOf(route));
            if (route.path().startsWith("/api/admin/")) {
                assertThat(annotations).as("@PreAuthorize reached from %s", route).isNotEmpty();
            }
            for (Set<String> annotationRoles : annotations) {
                assertThat(annotationRoles).as("@PreAuthorize reached from %s", route).isEqualTo(chainRoles);
                annotated++;
            }
        }
        assertThat(annotated).as("annotated service methods compared").isGreaterThanOrEqualTo(8);
    }

    @SuppressWarnings("unchecked")
    private AuthorizationManager<HttpServletRequest> chainAuthorization() {
        return filterChainProxy.getFilterChains().stream()
                .flatMap(chain -> chain.getFilters().stream())
                .filter(AuthorizationFilter.class::isInstance)
                .map(filter -> (AuthorizationManager<HttpServletRequest>)
                        ((AuthorizationFilter) filter).getAuthorizationManager())
                .findFirst().orElseThrow();
    }

    /** {@code role} signed in with both factors issued now, so the factor rules pass and only the role decides. */
    private Authentication holding(String role) {
        return UsernamePasswordAuthenticationToken.authenticated("matrix-" + role, null, List.of(
                new SimpleGrantedAuthority("ROLE_" + role),
                FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)
                        .issuedAt(clock.instant()).build(),
                FactorGrantedAuthority.withAuthority(TotpFactorGrant.AUTHORITY).issuedAt(clock.instant()).build()));
    }

    private MockHttpServletRequest request(Route route) {
        String path = route.path().replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
        MockHttpServletRequest request = new MockHttpServletRequest(web.getServletContext(), route.method().name(),
                path);
        request.setServletPath(path);
        return request;
    }

    private Method handlerOf(Route route) throws Exception {
        HandlerExecutionChain handler = handlerMapping.getHandler(request(route));
        assertThat(handler).as("handler for %s", route).isNotNull();
        return ((HandlerMethod) handler.getHandler()).getMethod();
    }

    /** The role sets of every {@code @PreAuthorize} on the methods {@code handler} calls. */
    private static Set<Set<String>> preAuthorizeRoles(Method handler) {
        JavaMethod method = MAIN.get(handler.getDeclaringClass()).getMethod(handler.getName(),
                handler.getParameterTypes());
        return method.getMethodCallsFromSelf().stream()
                .flatMap(call -> call.getTarget().resolveMember().stream())
                .filter(target -> target.isAnnotatedWith(PreAuthorize.class))
                .map(MatrixMethodSecurityAgreementTest::roles)
                .collect(Collectors.toSet());
    }

    private static Set<String> roles(JavaCodeUnit target) {
        String expression = target.getAnnotationOfType(PreAuthorize.class).value();
        Matcher matcher = ROLE_EXPRESSION.matcher(expression);
        assertThat(matcher.matches()).as("%s repeats a role check only: %s", target.getFullName(), expression)
                .isTrue();
        return Arrays.stream(matcher.group(1).split(","))
                .map(role -> role.strip().replace("'", ""))
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
