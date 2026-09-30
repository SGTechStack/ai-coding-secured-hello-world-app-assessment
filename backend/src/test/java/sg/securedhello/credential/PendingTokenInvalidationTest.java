package sg.securedhello.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.password.PasswordService;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.AdminCredentialCalls;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.PasswordResets;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.Registrations;
import sg.securedhello.testsupport.TotpFactors;
import sg.securedhello.user.UserAccountRepository;

/**
 * ADR-007's pending-token invalidation triggers, each through the path that fires it in the application: a pending
 * token of the affected type is then refused as {@code RESET_TOKEN_INVALID} at its redemption endpoint.
 */
class PendingTokenInvalidationTest extends CtxDefaultTest {

    /** The five triggers of T-CRED-019. */
    enum Trigger {
        /** Any successful password set through {@code PasswordService}: the pending reset tokens. */
        PASSWORD_SET,
        /** A new issuance of the same type: the earlier pending token. */
        NEW_ISSUANCE,
        /** An admin disable: pending tokens of both types. */
        ADMIN_DISABLE,
        /** An admin delete: the {@code ON DELETE CASCADE} removes the rows, so no call has to be remembered. */
        ADMIN_DELETE,
        /** A re-registration against an unactivated record: its activation token. */
        RE_REGISTRATION
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpSecretCipher cipher;

    @Autowired
    private PasswordService passwords;

    @Autowired
    private UserAccountRepository users;

    private Accounts accounts;
    private PasswordResets resets;
    private Registrations registrations;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
        resets = new PasswordResets(mockMvc);
        registrations = new Registrations(mockMvc);
    }

    /** A pending self-service reset token for {@code account}. */
    private String resetToken(Account account) throws Exception {
        resets.request(PasswordResets.emailOf(account)).andExpect(status().isAccepted());
        return emails.latestToken(PasswordResets.emailOf(account), CredentialTokenType.PASSWORD_RESET).orElseThrow();
    }

    /** A pending registration's activation token for {@code email}. */
    private String activationToken(String username, String email) throws Exception {
        registrations.register(username, email).andExpect(status().isAccepted());
        return emails.latestToken(email, CredentialTokenType.ACTIVATION).orElseThrow();
    }

    private ResultActions disable(UUID id) throws Exception {
        AdminCredentialCalls admin = AdminCredentialCalls.signedIn(mockMvc, new TotpFactors(jdbc, cipher, clock),
                accounts.withRole("ADMIN"));
        return mockMvc.perform(put("/api/admin/users/" + id + "/enabled").with(admin.session().inHeader())
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"));
    }

    private UUID idOf(String username) {
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", UUID.class, username);
    }

    @ParameterizedTest
    @EnumSource(Trigger.class)
    @Proves("T-CRED-019")
    void aPendingTokenOfTheAffectedTypeIsInvalidAfterTheTrigger(Trigger trigger) throws Exception {
        Account account = accounts.user();
        String username = Registrations.freshUsername();
        String email = Registrations.emailFor(username);
        List<String> invalidResets = List.of();
        List<String> invalidActivations = List.of();

        switch (trigger) {
            case PASSWORD_SET -> {
                String token = resetToken(account);
                passwords.setPassword(account.id(), PasswordResets.NEW_PASSWORD);
                invalidResets = List.of(token);
            }
            case NEW_ISSUANCE -> {
                String first = resetToken(account);
                resetToken(account);
                String activation = activationToken(username, email);
                activationToken(username, email);
                invalidResets = List.of(first);
                invalidActivations = List.of(activation);
            }
            case ADMIN_DISABLE -> {
                String reset = resetToken(account);
                String activation = activationToken(username, email);
                disable(account.id()).andExpect(status().isOk());
                disable(idOf(username)).andExpect(status().isOk());
                invalidResets = List.of(reset);
                invalidActivations = List.of(activation);
            }
            case ADMIN_DELETE -> {
                String reset = resetToken(account);
                String activation = activationToken(username, email);
                users.deleteById(account.id());
                users.deleteById(idOf(username));
                invalidResets = List.of(reset);
                invalidActivations = List.of(activation);
            }
            case RE_REGISTRATION -> {
                String first = activationToken(username, email);
                activationToken(Registrations.freshUsername(), email);
                invalidActivations = List.of(first);
            }
        }

        assertThat(invalidResets.size() + invalidActivations.size()).as("the trigger cancels something").isPositive();
        for (String token : invalidResets) {
            resets.confirm(token, PasswordResets.NEW_PASSWORD).andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
        }
        for (String token : invalidActivations) {
            registrations.activate(token, Registrations.PASSWORD).andExpect(problem(ErrorCode.RESET_TOKEN_INVALID));
        }
    }
}
