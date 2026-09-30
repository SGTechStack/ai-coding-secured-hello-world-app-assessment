package sg.example.helloauth.account;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * The Bootstrap admin, created at startup when no active Admin exists (ADR-0008). Outside the dev
 * profile all three are required; there are no defaults.
 */
@ConfigurationProperties("app.admin")
record BootstrapAdminProperties(String username, String password, String email) {

    boolean isComplete() {
        return StringUtils.hasText(username) && StringUtils.hasText(password) && StringUtils.hasText(email);
    }
}
