package com.assessment.securedhelloworld.lifecycle;

import com.assessment.securedhelloworld.auth.AppUserDetails;
import com.assessment.securedhelloworld.logging.LogSanitizer;
import com.assessment.securedhelloworld.user.Role;
import com.assessment.securedhelloworld.user.User;
import com.assessment.securedhelloworld.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Periodic access review (IM8 ac-4): compares actually-granted privilege
 * against a declared baseline and revokes drift, and disables accounts
 * past their declared expiry. Distinct from
 * {@link DormantAccountDisablingJob} (IM8 ac-3), which is purely
 * inactivity-based and has no notion of a declared baseline or expiry
 * date.
 *
 * <p>Runs daily, well within the 5-day remediation window IM8 ac-4
 * requires. Single-instance assumption (no distributed lock) — see
 * {@link DormantAccountDisablingJob}'s javadoc for the same caveat.
 */
@Component
public class AccessReviewJob {

    private static final Logger log = LoggerFactory.getLogger(AccessReviewJob.class);

    private final UserRepository userRepository;
    private final SessionRegistry sessionRegistry;
    private final AccessReviewProperties accessReviewProperties;
    private final Clock clock;

    public AccessReviewJob(
            UserRepository userRepository,
            SessionRegistry sessionRegistry,
            AccessReviewProperties accessReviewProperties,
            Clock clock) {
        this.userRepository = userRepository;
        this.sessionRegistry = sessionRegistry;
        this.accessReviewProperties = accessReviewProperties;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.security.access-review.cron:0 30 3 * * *}")
    @Transactional
    public void reviewAccess() {
        revokeExpiredAccounts();
        revokeExcessivePrivilege();
    }

    private void revokeExpiredAccounts() {
        List<User> expired = userRepository.findExpiredEnabledAccounts(clock.instant());
        for (User user : expired) {
            user.setEnabled(false);
            userRepository.save(user);
            invalidateAllSessionsFor(user.getUsername());
            log.info("Access review: disabled expired account username={} accountExpiresAt={}",
                    LogSanitizer.sanitize(user.getUsername()), user.getAccountExpiresAt());
        }
        if (!expired.isEmpty()) {
            log.info("Access review: disabled {} expired account(s)", expired.size());
        }
    }

    private void revokeExcessivePrivilege() {
        Set<String> authorisedAdmins = Set.copyOf(accessReviewProperties.getAuthorisedAdminUsernames());
        List<User> currentAdmins = userRepository.findByRole(Role.ADMIN);

        for (User admin : currentAdmins) {
            if (!authorisedAdmins.contains(admin.getUsername())) {
                admin.setRole(Role.USER);
                userRepository.save(admin);
                invalidateAllSessionsFor(admin.getUsername());
                log.warn("Access review: revoked ADMIN from username={} (not on the declared authorised-admin baseline)",
                        LogSanitizer.sanitize(admin.getUsername()));
            }
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
