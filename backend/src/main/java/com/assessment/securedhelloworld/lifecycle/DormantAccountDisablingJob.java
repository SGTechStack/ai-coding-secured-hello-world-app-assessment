package com.assessment.securedhelloworld.lifecycle;

import com.assessment.securedhelloworld.auth.AppUserDetails;
import com.assessment.securedhelloworld.logging.LogSanitizer;
import com.assessment.securedhelloworld.user.User;
import com.assessment.securedhelloworld.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Disables accounts that have been dormant (no login) beyond the
 * configured threshold, per IM8 ac-3. Runs once daily; a single-instance
 * deployment is assumed (see {@code LoginAttemptService}'s own javadoc
 * for the same assumption elsewhere in this codebase) so no distributed
 * lock (e.g. ShedLock) is used — if this app is ever run with multiple
 * instances, add one so the job doesn't run redundantly on every
 * instance.
 */
@Component
public class DormantAccountDisablingJob {

    private static final Logger log = LoggerFactory.getLogger(DormantAccountDisablingJob.class);

    private final UserRepository userRepository;
    private final SessionRegistry sessionRegistry;
    private final Duration dormancyThreshold;
    private final Clock clock;

    public DormantAccountDisablingJob(
            UserRepository userRepository,
            SessionRegistry sessionRegistry,
            @Value("${app.security.account-lifecycle.dormancy-days:90}") long dormancyDays,
            Clock clock) {
        this.userRepository = userRepository;
        this.sessionRegistry = sessionRegistry;
        this.dormancyThreshold = Duration.ofDays(dormancyDays);
        this.clock = clock;
    }

    @Scheduled(cron = "${app.security.account-lifecycle.dormancy-check-cron:0 0 3 * * *}")
    @Transactional
    public void disableDormantAccounts() {
        Instant threshold = clock.instant().minus(dormancyThreshold);
        List<User> dormantAccounts = userRepository.findDormantEnabledAccounts(threshold);

        for (User user : dormantAccounts) {
            user.setEnabled(false);
            userRepository.save(user);
            invalidateAllSessionsFor(user.getUsername());
            log.info("Disabled dormant account username={} lastLoginAt={}",
                    LogSanitizer.sanitize(user.getUsername()), user.getLastLoginAt());
        }

        if (!dormantAccounts.isEmpty()) {
            log.info("Dormant account sweep disabled {} account(s)", dormantAccounts.size());
        }
    }

    private void invalidateAllSessionsFor(String username) {
        sessionRegistry.getAllPrincipals().stream()
                .filter(principal -> principal instanceof AppUserDetails appUserDetails
                        && appUserDetails.getUsername().equals(username))
                .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
                .forEach(sessionInformation -> sessionInformation.expireNow());
    }
}
