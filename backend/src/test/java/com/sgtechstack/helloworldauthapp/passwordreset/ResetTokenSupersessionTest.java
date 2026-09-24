package com.sgtechstack.helloworldauthapp.passwordreset;

import com.sgtechstack.helloworldauthapp.auth.RequestRateLimiter;
import com.sgtechstack.helloworldauthapp.user.Role;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issuing a reset token must retire every outstanding one, so at most one live
 * path into an account exists at a time.
 *
 * <h2>The scenario that makes this matter</h2>
 *
 * Each request used to add a token and invalidate nothing, so N requests meant N
 * concurrent 30-minute windows. That is most dangerous in the situation the reset
 * flow is most often used in anger: someone who suspects their mailbox has been
 * read clicks "forgot password" again, reasonably believing the earlier link is
 * now void. It was not. Every link issued in the last half hour still granted a
 * takeover — including whichever one the attacker was holding.
 *
 * <p>So this is not really about token hygiene. It is about the reset flow
 * behaving the way the person using it believes it behaves.
 *
 * <h2>Retired, not deleted</h2>
 *
 * A superseded token is marked used. Following an old link then answers "this
 * reset token has already been used", which is true and explains what happened,
 * rather than "invalid token", which reads like a malfunction. The rows go later,
 * via {@link ExpiredTokenPurge}.
 *
 * <p>Works at the service layer rather than through HTTP because the assertions
 * are about token rows, and the reset-request endpoint deliberately reveals
 * nothing about them — its response is identical whether or not the email
 * resolves.
 */
@SpringBootTest
@ActiveProfiles("dev")
class ResetTokenSupersessionTest {

    private static final String USERNAME = "supersession-user";
    private static final String EMAIL = "supersession@example.com";

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RequestRateLimiter rateLimiter;

    private User user;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        rateLimiter.reset();

        user = userRepository.save(new User(USERNAME, EMAIL,
                passwordEncoder.encode("supersession-password-1234"), Role.USER, true));
    }

    @Test
    void asecondRequestRetiresTheTokenFromTheFirst() {
        passwordResetService.requestReset(EMAIL);
        passwordResetService.requestReset(EMAIL);

        List<PasswordResetToken> tokens = tokenRepository.findAllByUserOrderByExpiresAtAsc(user);

        assertThat(tokens).hasSize(2);
        assertThat(tokens.stream().filter(token -> !token.isUsed()).count())
                .as("exactly one live path into the account, no matter how many requests were made")
                .isEqualTo(1);
    }

    @Test
    void onlyTheNewestTokenRemainsUnused() {
        passwordResetService.requestReset(EMAIL);
        passwordResetService.requestReset(EMAIL);
        passwordResetService.requestReset(EMAIL);

        List<PasswordResetToken> outstanding = tokenRepository.findAllByUserAndUsedAtIsNull(user);

        assertThat(outstanding).hasSize(1);

        // The survivor must be the most recently issued one — retiring the new
        // token instead of the old ones would leave the user holding a link that
        // never works.
        List<PasswordResetToken> all = tokenRepository.findAllByUserOrderByExpiresAtAsc(user);
        assertThat(outstanding.get(0).getId()).isEqualTo(all.get(all.size() - 1).getId());
    }

    @Test
    void aSupersededTokenIsMarkedUsedRatherThanRemoved() {
        passwordResetService.requestReset(EMAIL);
        PasswordResetToken first = tokenRepository.findAllByUserOrderByExpiresAtAsc(user).get(0);

        passwordResetService.requestReset(EMAIL);

        PasswordResetToken reloaded = tokenRepository.findById(first.getId()).orElseThrow();
        // Kept, with usedAt set, so the row can still answer "was a reset
        // requested on this account, and what became of it" — and so a caller
        // following the old link gets an accurate message rather than a confusing
        // one.
        assertThat(reloaded.isUsed()).isTrue();
        assertThat(reloaded.getUsedAt()).isNotNull();
    }

    @Test
    void anUnregisteredEmailIssuesNothingAndRetiresNothing() {
        passwordResetService.requestReset(EMAIL);

        passwordResetService.requestReset("nobody-here@example.com");

        // The enumeration-resistance guarantee is that the response is identical.
        // It must not be bought by doing the work anyway: an unknown address must
        // not create a row, and must not disturb another account's token.
        assertThat(tokenRepository.findAllByUserAndUsedAtIsNull(user)).hasSize(1);
        assertThat(tokenRepository.findAll()).hasSize(1);
    }
}
