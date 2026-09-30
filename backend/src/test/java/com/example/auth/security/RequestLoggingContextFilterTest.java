package com.example.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.audit.AuditLogger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class RequestLoggingContextFilterTest {

    private final RequestLoggingContextFilter filter = new RequestLoggingContextFilter();

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    private Map<String, String> run(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        Map<String, String> seen = new HashMap<>();
        filter.doFilter(request, response, (req, res) -> seen.putAll(MDC.getCopyOfContextMap()));
        return seen;
    }

    @Test
    void safeCorrelationIdIsEchoedAndPutInMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
        request.addHeader(RequestLoggingContextFilter.CORRELATION_HEADER, "abc-DEF-12345678");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Map<String, String> mdc = run(request, response);

        assertThat(response.getHeader(RequestLoggingContextFilter.CORRELATION_HEADER)).isEqualTo("abc-DEF-12345678");
        assertThat(mdc).containsEntry("correlation.id", "abc-DEF-12345678");
    }

    @Test
    void unsafeOrMissingCorrelationIdIsReplacedByAGeneratedUuid() throws Exception {
        for (String bad : List.of("has spaces in it", "short", "x".repeat(65), "line\nbreak-12345", "semi;colon-123")) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
            request.addHeader(RequestLoggingContextFilter.CORRELATION_HEADER, bad);
            MockHttpServletResponse response = new MockHttpServletResponse();

            Map<String, String> mdc = run(request, response);

            String echoed = response.getHeader(RequestLoggingContextFilter.CORRELATION_HEADER);
            assertThat(echoed).as("for %s", bad).isNotEqualTo(bad);
            assertThat(UUID.fromString(echoed).toString()).isEqualTo(echoed);
            assertThat(mdc).containsEntry("correlation.id", echoed);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        run(new MockHttpServletRequest("GET", "/api/hello"), response);
        String generated = response.getHeader(RequestLoggingContextFilter.CORRELATION_HEADER);
        assertThat(UUID.fromString(generated).toString()).isEqualTo(generated);
    }

    @Test
    void sourceIpSessionHashAndUserIdAreInMdcDuringTheRequest() throws Exception {
        UUID publicId = UUID.randomUUID();
        AppUserDetails user = new AppUserDetails(
                publicId, "someone", "hash", true, true, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
        request.setRemoteAddr("10.1.2.3");
        request.setRequestedSessionId("raw-session-id");

        Map<String, String> mdc = run(request, new MockHttpServletResponse());

        assertThat(mdc).containsEntry("source.ip", "10.1.2.3");
        assertThat(mdc).containsEntry("session.hash", RequestLoggingContextFilter.sessionHash("raw-session-id"));
        assertThat(mdc.get("session.hash")).hasSize(16).doesNotContain("raw-session-id");
        assertThat(mdc).containsEntry(AuditLogger.USER_ID, publicId.toString());
        assertThat(mdc.values()).doesNotContain("someone");
    }

    @Test
    void anonymousRequestWithoutSessionHasNoUserIdOrSessionHash() throws Exception {
        Map<String, String> mdc = run(new MockHttpServletRequest("GET", "/api/hello"), new MockHttpServletResponse());

        assertThat(mdc).doesNotContainKeys(AuditLogger.USER_ID, "session.hash");
        assertThat(mdc).containsKeys("correlation.id", "source.ip");
    }

    @Test
    void mdcIsClearedAfterTheRequestEvenWhenTheChainThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
        request.setRequestedSessionId("sid");
        run(request, new MockHttpServletResponse());
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();

        assertThatThrownBy(() -> filter.doFilter(
                        new MockHttpServletRequest("GET", "/api/hello"), new MockHttpServletResponse(), (req, res) -> {
                            throw new IllegalStateException("boom");
                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void sessionHashIsStableAndTruncated() {
        assertThat(RequestLoggingContextFilter.sessionHash("abc")).isEqualTo(RequestLoggingContextFilter.sessionHash("abc"));
        assertThat(RequestLoggingContextFilter.sessionHash("abc")).isNotEqualTo(RequestLoggingContextFilter.sessionHash("abd"));
        assertThat(RequestLoggingContextFilter.sessionHash("abc")).matches("[0-9a-f]{16}");
    }
}
