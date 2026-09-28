package com.example.helloauth.bootstrap;

import com.example.helloauth.config.AppProperties;
import com.example.helloauth.domain.Role;
import com.example.helloauth.domain.User;
import com.example.helloauth.repo.UserRepository;
import com.example.helloauth.security.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;
    private final AuditLogger audit;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          AppProperties props, AuditLogger audit) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.audit = audit;
    }

    @Override
    public void run(String... args) {
        // Idempotent: only seed when no ADMIN exists (IM8 ac-6 seeding without duplication).
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }

        AppProperties.Admin cfg = props.getAdmin();
        if (cfg.getUsername() == null || cfg.getPassword() == null || cfg.getEmail() == null) {
            log.warn("No ADMIN present and app.admin.* not fully configured; skipping admin seed. "
                    + "Provide app.admin.username/email/password via secrets/env to bootstrap an admin.");
            return;
        }

        // Guard against a partial duplicate if the configured username/email is already used.
        if (userRepository.existsByUsername(cfg.getUsername()) || userRepository.existsByEmail(cfg.getEmail())) {
            log.warn("Configured admin username/email already exists; skipping admin seed.");
            return;
        }

        User admin = new User();
        admin.setUsername(cfg.getUsername());
        admin.setEmail(cfg.getEmail());
        admin.setPasswordHash(passwordEncoder.encode(cfg.getPassword()));
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        // IM8 ac-6: force a password change on first login for the seeded/temporary credential.
        admin.setMustChangePassword(true);
        userRepository.save(admin);

        audit.adminSeeded(cfg.getUsername());
        log.info("Seeded initial ADMIN account '{}' (must change password on first login).", cfg.getUsername());
    }
}
