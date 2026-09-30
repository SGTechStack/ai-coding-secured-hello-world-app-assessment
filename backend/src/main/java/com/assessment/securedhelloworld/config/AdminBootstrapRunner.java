package com.assessment.securedhelloworld.config;

import com.assessment.securedhelloworld.domain.Role;
import com.assessment.securedhelloworld.domain.User;
import com.assessment.securedhelloworld.repository.UserRepository;
import com.assessment.securedhelloworld.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the initial {@code ADMIN} account on first startup (PRD Story 12), so there is a way into
 * the admin module without a manual database edit. Runs once per application context: if an
 * {@code ADMIN} already exists (e.g. on every restart after the first), this is a deliberate
 * no-op — no duplicate seed account, no audit noise.
 *
 * <p>The seeded account is flagged {@code forcePasswordChange} so the operator-supplied bootstrap
 * credential cannot remain in permanent use (IM8 ac-6) — enforced by {@link AdminAccessConfig}'s
 * interceptor on every {@code /api/admin/**} call.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final String adminUsername;
    private final String adminPassword;

    public AdminBootstrapRunner(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditLogService auditLogService,
                                 @Value("${app.admin.username}") String adminUsername,
                                 @Value("${app.admin.password}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("No ADMIN user exists and app.admin.password is blank; skipping admin bootstrap. "
                    + "Set APP_ADMIN_USERNAME/APP_ADMIN_PASSWORD and restart to seed an admin account.");
            return;
        }

        String email = adminUsername + "@admin.local";
        User admin = new User(adminUsername, email, passwordEncoder.encode(adminPassword), Role.ADMIN);
        // Bootstrap credential must be changed before it is used for anything admin-related.
        admin.setForcePasswordChange(true);
        userRepository.save(admin);

        auditLogService.event("admin_bootstrap", "success", "system", adminUsername);
    }
}
