package com.assessment.auth.password;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Renders the reset link on the console, in the {@code dev} profile only (ticket 11).
 *
 * <p>There is no mail server in this application and none is in scope, so without this a developer
 * could never complete a reset flow locally. Writing the link through SLF4J instead would put a
 * live reset token into the ECS document, the rolling application file, and anything downstream of
 * them — which is precisely what ticket 11 forbids.
 *
 * <h2>Why this class is the one documented exemption from ArchUnit rule #1</h2>
 *
 * <p>Std_Logging:330 bans {@code System.out.println} and {@code e.printStackTrace()} application-
 * wide, and ticket 15 makes it an enforced static rule. Ticket 11 independently requires the reset
 * URL to reach the developer <em>without</em> passing through the logging framework. Those two
 * rulings meet here and only here.
 *
 * <p>The conflict is resolved in favour of both, by exempting exactly this class by name in {@code
 * ArchitectureRulesTest} rather than by softening the rule or by evading it through an aliased
 * {@code PrintStream}. The exemption is narrow, it is visible in the rule itself, and it is
 * profile-gated so the bean does not exist in any deployed profile. Widening it means editing the
 * rule, which is the point.
 */
@Component
@Profile("dev")
public class DevConsoleResetLinkChannel implements ResetLinkChannel {

  @Override
  public void deliver(String email, String token) {
    System.out.println(
        "[dev] password reset for " + email + " -> /reset-password/confirm?token=" + token);
  }
}
