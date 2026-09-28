package sg.securedhello.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;

/**
 * The error envelope from every producer that exists in {@code ctx-default} (ADR-031), and the authorization-matrix
 * skeleton's refusals (ADR-043). Each response is checked by {@code ProblemAssertions}: status, media type, schema,
 * no {@code BasicErrorController} members, no {@code WWW-Authenticate}.
 */
class ErrorEnvelopeTest extends CtxDefaultTest {

    /** The SPA's request headers, exactly as {@code frontend/src/lib/api/client.ts} exports them (T-AUTH-015). */
    static final String SPA_ACCEPT = "application/json, application/problem+json";

    /** A path no matrix row names. */
    private static final String UNMATCHED = "/api/no-such-route";

    private static final String ESCAPED_MESSAGE = "secret-internal-state-7f3a";

    static Stream<HttpMethod> everyMethod() {
        return Stream.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH,
                HttpMethod.DELETE, HttpMethod.OPTIONS);
    }

    @Test
    @Proves({"T-AUTH-009", "T-AUTH-007", "T-AUTH-011"})
    void anonymousJsonRequestToAProtectedPathGetsTheEntryPoint401() throws Exception {
        mockMvc.perform(get("/api/hello").accept(MediaType.APPLICATION_JSON))
                .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @Test
    @Proves("T-AUTH-015")
    void theSpaRequestHeadersGetTheProblemJson401() throws Exception {
        mockMvc.perform(get("/api/hello").header(HttpHeaders.ACCEPT, SPA_ACCEPT))
                .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/html", "*/*", "application/xml"})
    @Proves("T-AUTH-011")
    void anyAcceptHeaderStillGetsTheEnvelopeNotARedirectOr406(String accept) throws Exception {
        mockMvc.perform(get("/api/hello").header(HttpHeaders.ACCEPT, accept))
                .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @ParameterizedTest
    @ValueSource(strings = {UNMATCHED, "/", "/index.html", "/api"})
    @Proves({"T-ADM-007", "T-AUTH-011"})
    void anUnmatchedRouteRefusesAnAnonymousCallerWith401(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
    }

    @ParameterizedTest
    @ValueSource(strings = {UNMATCHED, "/", "/api/hello", "/api/admin/users"})
    @Proves({"T-ADM-007", "T-AUTH-011"})
    void anUnmatchedRouteRefusesASignedInUserWith403(String path) throws Exception {
        mockMvc.perform(get(path).with(user("user-adm007").roles("USER")))
                .andExpect(problem(ErrorCode.ACCESS_DENIED));
    }

    @ParameterizedTest
    @MethodSource("everyMethod")
    @Proves("T-ADM-019")
    void roleDefinitionPathsRefuseAnAnonymousCallerWith401(HttpMethod method) throws Exception {
        mockMvc.perform(roleDefinition(method).with(csrf()))
                .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
    }

    @ParameterizedTest
    @MethodSource("everyMethod")
    @Proves("T-ADM-020")
    void roleDefinitionPathsRefuseAUserWith403(HttpMethod method) throws Exception {
        mockMvc.perform(roleDefinition(method).with(csrf()).with(user("user-adm020").roles("USER")))
                .andExpect(problem(ErrorCode.ACCESS_DENIED));
    }

    @ParameterizedTest
    @MethodSource("everyMethod")
    @Proves("T-ADM-020")
    void roleDefinitionPathsRefuseAnAdminWith403(HttpMethod method) throws Exception {
        mockMvc.perform(roleDefinition(method).with(csrf()).with(user("admin-adm020").roles("ADMIN")))
                .andExpect(problem(ErrorCode.ACCESS_DENIED));
    }

    @Test
    @Proves({"T-AUTH-001", "T-AUTH-011"})
    void anExceptionReachingTheErrorDispatchIsAnInternalErrorWithoutItsMessage() throws Exception {
        String body = mockMvc.perform(errorDispatch("/api/boom", 500)
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException(ESCAPED_MESSAGE)))
                .andExpect(problem(ErrorCode.INTERNAL_ERROR))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).doesNotContain(ESCAPED_MESSAGE).doesNotContain("IllegalStateException")
                .contains("\"instance\":\"/api/boom\"")
                .contains("\"detail\":\"" + ErrorCode.INTERNAL_ERROR.detail() + "\"");
    }

    static Stream<Arguments> containerStatuses() {
        return Stream.of(Arguments.of(400, ErrorCode.VALIDATION_FAILED), Arguments.of(404, ErrorCode.ACCESS_DENIED),
                Arguments.of(413, ErrorCode.VALIDATION_FAILED), Arguments.of(503, ErrorCode.INTERNAL_ERROR));
    }

    @ParameterizedTest
    @MethodSource("containerStatuses")
    @Proves("T-AUTH-011")
    void aContainerStatusReachingTheErrorDispatchIsMappedToACode(int status, ErrorCode expected) throws Exception {
        mockMvc.perform(errorDispatch("/api/anything", status)).andExpect(problem(expected));
    }

    @Test
    @Proves("T-AUTH-011")
    void theFrameworkLogoutPathIsAnUnmatchedRouteNotARedirect() throws Exception {
        mockMvc.perform(post("/logout").with(csrf()).with(user("user-logout").roles("USER")))
                .andExpect(problem(ErrorCode.ACCESS_DENIED))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @Test
    void anAnonymousBrowserRefusalCreatesNoSession() throws Exception {
        mockMvc.perform(get("/api/hello").accept(MediaType.TEXT_HTML))
                .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED))
                .andExpect(MockMvcResultMatchers.request().sessionAttributeDoesNotExist("SPRING_SECURITY_SAVED_REQUEST"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @Proves("T-AUTH-011")
    void theErrorPathItselfIsNotAnOpenRoute() throws Exception {
        mockMvc.perform(get("/error")).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
    }

    @Test
    @Proves("T-AUTH-011")
    void aFirewallRejectionIsAValidationFailureNotAContainerPage() throws Exception {
        mockMvc.perform(get("/api/hello;jsessionid=x")).andExpect(problem(ErrorCode.VALIDATION_FAILED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/info"})
    @Proves("T-AUTH-011")
    void actuatorRefusalsUseTheEnvelopeToo(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
        mockMvc.perform(get(path).with(user("admin-actuator").roles("ADMIN")))
                .andExpect(problem(ErrorCode.ACCESS_DENIED));
    }

    @Test
    void theHealthBodyIsActuatorsOwnFormatNotTheEnvelope() throws Exception {
        String contentType = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentType();
        assertThat(MediaType.parseMediaType(contentType).isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .isFalse();
    }

    private static MockHttpServletRequestBuilder roleDefinition(HttpMethod method) {
        return request(method, "/api/admin/roles/{name}", "ADMIN").contentType(MediaType.APPLICATION_JSON)
                .content(method == HttpMethod.GET || method == HttpMethod.HEAD ? "" : "{}");
    }

    /** What the container does after an escaped exception or a status: an ERROR dispatch to {@code /error}. */
    private static MockHttpServletRequestBuilder errorDispatch(String originalUri, int status) {
        RequestPostProcessor asErrorDispatch = request -> {
            request.setDispatcherType(DispatcherType.ERROR);
            return request;
        };
        return get("/error").with(asErrorDispatch)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, originalUri)
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, status);
    }
}
