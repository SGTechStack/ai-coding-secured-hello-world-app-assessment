package sg.securedhello.security.login;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import sg.securedhello.session.SessionAttributes;
import sg.securedhello.testsupport.Proves;

/** The login composite's interval reset, on its own (ADR-038). */
class IdleIntervalResetTest {

    @Test
    @Proves("T-SES-034")
    void theResetSetsWAndNeverReStampsTheAuthInstantHoweverOftenItRuns() {
        Instant authInstant = Instant.parse("2026-01-01T00:00:00Z");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionAttributes.AUTH_INSTANT, authInstant);
        session.setMaxInactiveInterval(120);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        IdleIntervalReset reset = new IdleIntervalReset(Duration.ofMinutes(15));

        reset.onAuthentication(null, request, new MockHttpServletResponse());
        reset.onAuthentication(null, request, new MockHttpServletResponse());

        assertThat(session.getMaxInactiveInterval()).isEqualTo(900);
        assertThat(session.getAttribute(SessionAttributes.AUTH_INSTANT)).isSameAs(authInstant);
    }
}
