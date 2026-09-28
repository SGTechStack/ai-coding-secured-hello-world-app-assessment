package sg.securedhello.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
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

import sg.securedhello.testsupport.Proves;

/** The anonymous branch of the absolute-lifetime filter, against a session whose times the test controls. */
class AbsoluteLifetimeFilterTest {

    private static final Duration W = Duration.ofMinutes(15);
    private static final long CREATED = 1_700_000_000_000L;

    private final AbsoluteLifetimeFilter filter = new AbsoluteLifetimeFilter(W);

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
    void aRequestWithoutASessionPassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(request.getSession(false)).isNull();
    }
}
