package com.example.helloauth.service;

import com.example.helloauth.domain.Account;
import com.example.helloauth.domain.Role;
import com.example.helloauth.repository.AccountRepository;
import com.example.helloauth.service.exception.AuthExceptions.RegistrationConflictException;
import java.time.Clock;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicy;
    private final AuditLog audit;
    private final Clock clock;

    public RegistrationService(
            AccountRepository accounts,
            PasswordEncoder passwordEncoder,
            PasswordPolicyValidator passwordPolicy,
            AuditLog audit,
            Clock clock) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Creates an account with role {@code USER}.
     *
     * <p>Deliberately does not log the visitor in. Story 1 says an account is created and says
     * nothing about a session; story 2 owns session creation. Keeping it that way means there is
     * exactly one code path that issues a session.
     *
     * <p>The policy check runs before the uniqueness checks so that a weak password is reported even
     * when the username is also taken — a visitor should not have to fix one problem to discover the
     * next.
     */
    @Transactional
    public Account register(String username, String email, String rawPassword) {
        passwordPolicy.validate(rawPassword);

        // Normalised on the way in, so the unique constraint enforces case-insensitivity at the
        // database level rather than relying on the application to remember to ask nicely.
        String normalisedUsername = normalise(username);
        String normalisedEmail = normalise(email);

        if (accounts.existsByUsername(normalisedUsername)) {
            throw new RegistrationConflictException("username", "That username is already taken.");
        }
        if (accounts.existsByEmail(normalisedEmail)) {
            throw new RegistrationConflictException("email", "That email is already registered.");
        }

        Account account =
                accounts.save(
                        new Account(
                                normalisedUsername,
                                normalisedEmail,
                                passwordEncoder.encode(rawPassword),
                                Role.USER,
                                clock.instant()));

        // The account id and username are logged. The password is not, here or anywhere.
        audit.accountRegistered(account.getUsername(), account.getId());
        return account;
    }

    static String normalise(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
