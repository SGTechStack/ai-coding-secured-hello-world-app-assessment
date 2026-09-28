package com.example.auth.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/**
 * Records every login failure's source IP into {@link IpLoginThrottleService},
 * mirroring {@link LoginAttemptListener}'s per-account listener but keyed by
 * IP instead of username -- so a spray of failures across many different
 * usernames from one IP is still caught, even though no single account ever
 * reaches its own lockout threshold.
 *
 * <p>The IP comes from {@code Authentication#getDetails()}, a {@link
 * WebAuthenticationDetails} that {@code AbstractAuthenticationProcessingFilter}
 * already attaches to every authentication attempt -- no new wiring needed.
 */
@Component
public class IpThrottleListener {

    private final IpLoginThrottleService ipLoginThrottleService;

    public IpThrottleListener(IpLoginThrottleService ipLoginThrottleService) {
        this.ipLoginThrottleService = ipLoginThrottleService;
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        Object details = event.getAuthentication().getDetails();
        if (details instanceof WebAuthenticationDetails webDetails) {
            ipLoginThrottleService.recordFailure(webDetails.getRemoteAddress());
        }
    }
}
