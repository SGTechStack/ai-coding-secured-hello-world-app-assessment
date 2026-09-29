package hello.desk.auth;

import hello.desk.config.AppProperties;
import hello.desk.security.PasswordPolicy;
import hello.desk.user.Role;
import hello.desk.user.UserAccount;
import hello.desk.user.UserAccountRepository;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    public AdminSeeder(UserAccountRepository users, PasswordEncoder passwordEncoder, AppProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }
        String password = properties.admin().password();
        if (!PasswordPolicy.isAcceptable(password)) {
            throw new IllegalStateException("app.admin.password must be 12 to 128 characters");
        }
        UserAccount admin = new UserAccount();
        admin.setUsername(properties.admin().username().trim());
        admin.setEmail(properties.admin().email().trim().toLowerCase(Locale.ROOT));
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        admin.setFailedLoginAttempts(0);
        admin.setCreatedAt(Instant.now());
        users.save(admin);
        log.info("audit event=admin_seeded username={}", admin.getUsername());
    }
}
