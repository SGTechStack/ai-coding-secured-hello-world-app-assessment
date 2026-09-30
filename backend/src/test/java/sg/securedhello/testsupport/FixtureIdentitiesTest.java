package sg.securedhello.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import sg.securedhello.user.Identifiers;

/** The fixture allocator hands out synthetic identities only, once each per run (Std §5:525; MFA §5:408). */
class FixtureIdentitiesTest {

    @Test
    @Proves("T-ARCH-003")
    void everyMintedIdentityIsFreshCanonicalAndUnderTheReservedDomain() {
        Set<String> usernames = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            String username = switch (i % 3) {
                case 0 -> FixtureIdentities.username("u", 17);
                case 1 -> Registrations.freshUsername();
                default -> Accounts.unknownUsername();
            };
            assertThat(usernames.add(username)).as("%s handed out twice", username).isTrue();
            assertThat(username).doesNotContain("@");
            assertThat(Identifiers.validUsername(username)).as("%s is a canonical username", username).isTrue();
            assertThat(FixtureIdentities.email(username)).isEqualTo(username + "@example.test");
        }
        assertThat(Registrations.emailFor("someone")).endsWith("@" + FixtureIdentities.DOMAIN);
        assertThat(PasswordResets.emailOf(new Accounts.Account(UUID.randomUUID(), "someone", "x")))
                .endsWith("@" + FixtureIdentities.DOMAIN);
    }

    @Test
    void aPrefixThatCouldNotBeCanonicalIsRefused() {
        assertThatIllegalArgumentException().isThrownBy(() -> FixtureIdentities.username("Upper", 12));
        assertThatIllegalArgumentException().isThrownBy(() -> FixtureIdentities.username("at@", 12));
    }
}
