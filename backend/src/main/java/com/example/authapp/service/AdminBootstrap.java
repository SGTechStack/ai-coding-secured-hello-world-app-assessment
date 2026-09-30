package com.example.authapp.service;

import com.example.authapp.config.AppProperties;
import com.example.authapp.domain.Role;
import com.example.authapp.domain.User;
import com.example.authapp.domain.UserRepository;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds the first admin from configuration if (and only if) no ADMIN exists. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final AppProperties props;

    public AdminBootstrap(UserRepository users, PasswordEncoder encoder, PasswordPolicy policy, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.policy = policy;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }
        AppProperties.Admin admin = props.admin();
        if (admin.password() == null || admin.password().isBlank()) {
            LOG.warn("No ADMIN user exists and app.admin.password is not set; skipping admin seed.");
            return;
        }
        policy.validate(admin.password());
        users.save(new User(admin.username(), admin.email().toLowerCase(Locale.ROOT),
                encoder.encode(admin.password()), Role.ADMIN));
        LOG.info("Seeded initial admin account '{}'", admin.username());
    }
}
