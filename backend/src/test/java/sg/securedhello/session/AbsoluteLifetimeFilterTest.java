package sg.securedhello.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;
import sg.securedhello.testsupport.MutableClock;
import sg.securedhello.testsupport.Proves;

/** Both branches of the absolute-lifetime filter, against a session whose times the test controls. */
class AbsoluteLifetimeFilterTest {

    private static final Duration W = Duration.ofMinutes(15);
    private static final long CREATED = 1_700_000_000_000L;

    private static final Duration ABSOLUTE = Duration.ofHours(8);

    private final MutableClock clock = MutableClock.startingAt(Instant.ofEpochMilli(CREATED));
    private final ProblemDetailWriter writer = mock(ProblemDetailWriter.class);
    private final AbsoluteLifetimeFilter filter = new AbsoluteLifetimeFilter(W, ABSOLUTE, clock, writer,
            mock(AuditEmitter.class));

    private HttpSession anonymousSession(long lastAccessed) throws Exception {
        HttpSession session = mock(HttpSession.class);
        when(session.getCreationTime()).thenReturn(CREATED);
        when(session.getLastAccessedTime()).thenReturn(lastAccessed);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return session;
    }

    @Test
    void insideTheWindowTheIntervalIsShortenedToWhatIsLeft() throws Exception {
        HttpSession session = anonymousSession(CREATED + Duration.ofMinutes(10).toMillis());

        verify(session).setMaxInactiveInterval((int) Duration.ofMinutes(5).toSeconds());
        verify(session, never()).invalidate();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 999, 60_000})
    @Proves("T-SES-024")
    void atOrPastTheWindowTheSessionIsInvalidatedNeverGivenANonPositiveInterval(long pastW) throws Exception {
        HttpSession session = anonymousSession(CREATED + W.toMillis() - 999 + pastW);

        verify(session).invalidate();
        verify(session, never()).setMaxInactiveInterval(anyInt());
    }

    @Test
    void anAuthenticatedSessionIsLeftToTheAuthInstantBranch() throws Exception {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute(SessionAttributes.AUTH_INSTANT)).thenReturn(Instant.ofEpochMilli(CREATED));
        when(session.getCreationTime()).thenReturn(CREATED);
        when(session.getLastAccessedTime()).thenReturn(CREATED + W.toMillis() * 2);  // well inside 8 h of AUTH_INSTANT
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        verify(session, never()).invalidate();
        verify(session, never()).setMaxInactiveInterval(anyInt());
    }

    @Test
    void anAuthenticatedSessionPastItsAbsoluteLifetimeIsInvalidatedAndAnswered401() throws Exception {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute(SessionAttributes.AUTH_INSTANT)).thenReturn(Instant.ofEpochMilli(CREATED));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        clock.advance(ABSOLUTE);

        filter.doFilter(request, response, chain);

        verify(session).invalidate();
        verify(writer).write(eq(request), eq(response), eq(ErrorCode.AUTHENTICATION_FAILED));
        assertThat(chain.getRequest()).as("the chain did not continue").isNull();
    }

    @Test
    void anAuthenticatedSessionJustInsideItsAbsoluteLifetimeContinues() throws Exception {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute(SessionAttributes.AUTH_INSTANT))
                .thenReturn(clock.instant().minus(ABSOLUTE).plusMillis(1));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(session, never()).invalidate();
        verify(writer, never()).write(any(), any(), any());
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void aRequestWithoutASessionPassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(request.getSession(false)).isNull();
    }
}
