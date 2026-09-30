package com.assessment.hello.config;

import com.assessment.hello.domain.Role;
import com.assessment.hello.domain.User;
import com.assessment.hello.repository.UserRepository;
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
    private final AppProperties appProperties;

    public AdminBootstrap(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          AppProperties appProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            log.info("Admin bootstrap: an ADMIN already exists, skipping seed.");
            return;
        }

        AppProperties.Admin adminCfg = appProperties.getAdmin();
        if (adminCfg.getUsername() == null || adminCfg.getPassword() == null) {
            log.warn("Admin bootstrap: no app.admin credentials configured, skipping seed.");
            return;
        }

        User admin = new User();
        admin.setUsername(adminCfg.getUsername());
        admin.setEmail(adminCfg.getEmail() != null ? adminCfg.getEmail() : "admin@example.com");
        admin.setPasswordHash(passwordEncoder.encode(adminCfg.getPassword()));
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        userRepository.save(admin);
        log.info("Admin bootstrap: seeded initial ADMIN username={}", admin.getUsername());
    }
}
