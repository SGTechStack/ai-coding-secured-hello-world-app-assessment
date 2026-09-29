package sg.securedhello.security.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.util.ReflectionTestUtils;

import sg.securedhello.testsupport.Proves;
import sg.securedhello.user.SignedInUser;
import sg.securedhello.user.UserAccount;
import sg.securedhello.user.UserAccountRepository;

/**
 * The forced-change expiry lives in the pre-authentication checks only (ADR-046): the principal's
 * {@code isCredentialsNonExpired()} is hard-wired {@code true}, so the provider's post-authentication checks, which run
 * only after a correct password, can never emit it.
 */
class ForcedChangeExpiryCheckTest {

    private static final Instant NOW = Instant.parse("2026-09-29T00:00:00Z");
    private static final String PASSWORD = "correct-horse-battery-staple";

    private static UserAccount issuedAt(Instant issuedAt) {
        UserAccount account = UserAccount.administrator("seeded-admin", "seeded-admin@admin.invalid", issuedAt);
        account.issueCredential("{noop}" + PASSWORD, issuedAt);
        return account;
    }

    private static final Instant EXPIRED = NOW.minus(UserAccount.FORCED_CHANGE_GRACE).minus(Duration.ofNanos(1000));

    @Test
    @Proves("T-ADM-030")
    void anExpiredCredentialIsRefusedBeforeThePasswordAndNeverByThePostAuthenticationChecks() {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        UserAccount account = issuedAt(EXPIRED);
        when(accounts.findByUsername("seeded-admin")).thenReturn(Optional.of(account));
        DaoAuthenticationProvider provider = new SignInConfig().passwordAuthenticationProvider(accounts,
                PasswordEncoderFactories.createDelegatingPasswordEncoder(), Clock.fixed(NOW, ZoneOffset.UTC));
        SignedInUser user = SignedInUser.of(account, NOW);

        assertThat(user.isCredentialsNonExpired()).isTrue();
        assertThat(user.forcedChangeExpired()).isTrue();
        UserDetailsChecker post = (UserDetailsChecker) ReflectionTestUtils.getField(provider,
                "postAuthenticationChecks");
        assertThatCode(() -> post.check(user)).doesNotThrowAnyException();
        UserDetailsChecker pre = (UserDetailsChecker) ReflectionTestUtils.getField(provider, "preAuthenticationChecks");
        assertThat(pre).isInstanceOf(PreAuthenticationChecks.class);
        assertThatThrownBy(() -> pre.check(user)).isInstanceOf(CredentialsExpiredException.class);
        assertThatThrownBy(() -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("seeded-admin", PASSWORD)))
                .as("the correct password").isInstanceOf(CredentialsExpiredException.class);
        assertThatThrownBy(() -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("seeded-admin", "wrong-password-entirely")))
                .as("a wrong password").isInstanceOf(CredentialsExpiredException.class);
    }

    @Test
    void theGraceIsThirtyDaysAndAnUnforcedOrUnstampedAccountNeverExpires() {
        assertThat(UserAccount.FORCED_CHANGE_GRACE).isEqualTo(Duration.ofDays(30));
        assertThat(issuedAt(NOW.minus(Duration.ofDays(30))).forcedChangeExpiredAt(NOW)).isFalse();
        assertThat(issuedAt(EXPIRED).forcedChangeExpiredAt(NOW)).isTrue();

        UserAccount changed = issuedAt(EXPIRED);
        changed.replacePasswordHash("{noop}" + PASSWORD);
        assertThat(changed.isForcePasswordChange()).isFalse();
        assertThat(changed.forcedChangeExpiredAt(NOW)).isFalse();
        assertThat(SignedInUser.of(changed, NOW).passwordChangeRequired()).isFalse();
    }

    @Test
    void aCompletedChangeFreesThePrincipal() {
        SignedInUser user = SignedInUser.of(issuedAt(NOW), NOW);
        assertThat(user.passwordChangeRequired()).isTrue();
        assertThat(user.forcedChangeExpired()).isFalse();

        SignedInUser changed = user.withPasswordChanged();
        assertThat(changed.passwordChangeRequired()).isFalse();
        assertThat(changed.getUsername()).isEqualTo(user.getUsername());
        assertThat(changed.getAuthorities()).isEqualTo(user.getAuthorities());
    }
}
