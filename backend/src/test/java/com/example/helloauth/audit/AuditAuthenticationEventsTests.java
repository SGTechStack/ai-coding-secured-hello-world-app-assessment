package com.example.helloauth.audit;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationFailureServiceExceptionEvent;
import org.springframework.security.core.Authentication;

/**
 * Narrow-seam coverage for the {@code reasonOf} branches the HTTP suite
 * cannot reach (same ListAppender idiom as {@link AuditLoggerTests}):
 * {@code LoginService}'s lock gate throws before {@code authenticate()}
 * runs, so the provider never publishes a {@link LockedException} event at
 * the HTTP seam — and no request path produces an unmapped exception type
 * at all. The concrete failure events the
 * {@code DefaultAuthenticationEventPublisher} emits are fed to the listener
 * directly.
 */
class AuditAuthenticationEventsTests {

    private final AuditAuthenticationEvents events =
        new AuditAuthenticationEvents(new AuditLogger());
    private final Logger logger =
        (Logger) LoggerFactory.getLogger(AuditLogger.LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    @Test
    void providerLockedExceptionMapsToLockedReason() {
        // The provider's isAccountNonLocked backstop — unreachable through
        // the service-level gate, but the mapping must still be stable if a
        // future flow ever lets it through.
        events.onAuthenticationFailure(new AuthenticationFailureLockedEvent(
            principal(), new LockedException("provider backstop")));

        assertThat(markerText())
            .contains("event=login_failure")
            .contains("actor=alice")
            .contains("reason=locked");
    }

    @Test
    void disabledExceptionMapsToDisabledReason() {
        events.onAuthenticationFailure(new AuthenticationFailureDisabledEvent(
            principal(), new DisabledException("account disabled")));

        assertThat(markerText())
            .contains("event=login_failure")
            .contains("actor=alice")
            .contains("reason=disabled");
    }

    @Test
    void unmappedExceptionCollapsesToUnknownReason() {
        // Any AuthenticationException outside the named set must land on
        // "unknown" — never the class name or the free-form message, which
        // downstream parsers cannot switch on.
        events.onAuthenticationFailure(
            new AuthenticationFailureServiceExceptionEvent(principal(),
                new AuthenticationServiceException("token backend down")));

        assertThat(markerText())
            .contains("event=login_failure")
            .contains("actor=alice")
            .contains("reason=unknown")
            .doesNotContain("token backend down")
            .doesNotContain("AuthenticationServiceException");
    }

    private static Authentication principal() {
        return UsernamePasswordAuthenticationToken.unauthenticated("alice", "x");
    }

    private String markerText() {
        assertThat(appender.list).singleElement();
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getMarkerList()).isNotEmpty();
        return event.getMarkerList() + event.getFormattedMessage();
    }
}
