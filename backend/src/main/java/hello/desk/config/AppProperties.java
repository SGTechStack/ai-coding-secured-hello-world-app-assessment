package hello.desk.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Admin admin, Security security, Cors cors) {

    public record Admin(String username, String password, String email) {
    }

    public record Security(
            int lockoutMaxAttempts,
            Duration lockoutDuration,
            int ipMaxAttempts,
            Duration ipWindow,
            Duration passwordResetTtl,
            int bcryptStrength,
            boolean secureCookies,
            String frontendOrigin) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
