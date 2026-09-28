package com.example.helloworldauth.auth;

import com.example.helloworldauth.user.User;
import org.springframework.stereotype.Component;

/**
 * Seam for account-lockout policy. Ticket 04 wires the counter; ticket 07
 * replaces this default with the real threshold-based lockout that sets
 * {@code locked_until}. Keeping it a bean now means ticket 07 only swaps the
 * body, not the login flow.
 */
@Component
public class LoginAttemptPolicy {

    /** Called after a failed attempt, with the counter already incremented. */
    public void onFailure(User user) {
        // No-op until ticket 07 (lockout threshold + locked_until).
    }
}
