package com.assessment.securedhelloworld.lifecycle;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The declared baseline of usernames authorised to hold the ADMIN role,
 * used by {@link AccessReviewJob} to detect privilege drift (IM8 ac-4):
 * any account holding ADMIN that is NOT on this list is flagged as
 * excessive privilege. This is the intentional source of truth for who
 * *should* be an admin, independent of what the database currently says.
 */
@Component
@ConfigurationProperties(prefix = "app.security.access-review")
public class AccessReviewProperties {

    /**
     * Usernames allowed to hold the ADMIN role. The bootstrap admin
     * account's username is included by default; override in
     * configuration to reflect the actual set of intentionally
     * provisioned administrators.
     */
    private List<String> authorisedAdminUsernames = List.of("admin");

    public List<String> getAuthorisedAdminUsernames() {
        return authorisedAdminUsernames;
    }

    public void setAuthorisedAdminUsernames(List<String> authorisedAdminUsernames) {
        this.authorisedAdminUsernames = authorisedAdminUsernames;
    }
}
