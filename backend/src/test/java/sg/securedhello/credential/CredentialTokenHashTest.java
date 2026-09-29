package sg.securedhello.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Token generation, shape and the domain-separated stored form (ADR-007), as pure functions (level U). */
class CredentialTokenHashTest {

    /** 43 Base64url characters: a token's shape. */
    private static final String SAMPLE = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJK-_0123";

    @Test
    void aTokenIs256RandomBitsAsUnpaddedBase64url() {
        SecureRandom random = new SecureRandom();
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String token = CredentialTokenHash.generate(random);
            assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]{43}");
            assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
            assertThat(CredentialTokenHash.wellFormed(token)).isTrue();
            tokens.add(token);
        }
        assertThat(tokens).hasSize(50);
    }

    @Test
    void theStoredFormIsLowercaseHexSha256OfTheTypeLabelColonAndToken() {
        // printf 'ACTIVATION:abc' | sha256sum; printf 'PASSWORD_RESET:abc' | sha256sum
        assertThat(CredentialTokenHash.hash(CredentialTokenType.ACTIVATION, "abc"))
                .isEqualTo("6c26e2439f5fd047d9e026cbfc368196361e9ab943c9d4813d2d4fc3849db513");
        assertThat(CredentialTokenHash.hash(CredentialTokenType.PASSWORD_RESET, "abc"))
                .isEqualTo("230dcac5d7e22e5bf253b48ed358966fa5f1dc285b5787593724791f0364842a");
    }

    @Test
    void theTypeLabelSeparatesTheDomains() {
        assertThat(CredentialTokenHash.hash(CredentialTokenType.ACTIVATION, SAMPLE))
                .isNotEqualTo(CredentialTokenHash.hash(CredentialTokenType.PASSWORD_RESET, SAMPLE));
        assertThat(CredentialTokenHash.hash(CredentialTokenType.ACTIVATION, SAMPLE))
                .isNotEqualTo(CredentialTokenHash.hash(CredentialTokenType.ACTIVATION, SAMPLE + "x"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJK-_012",
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJK-_01234", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJK+/0123",
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJK-_012=", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJK-_:123",
            " bcdefghijklmnopqrstuvwxyzABCDEFGHIJK-_0123"})
    void anythingButFortyThreeBase64urlCharactersIsMisshapen(String submitted) {
        assertThat(CredentialTokenHash.wellFormed(submitted)).isFalse();
    }

    @Test
    void aFortyThreeCharacterBase64urlValueIsWellFormed() {
        assertThat(CredentialTokenHash.wellFormed(SAMPLE)).isTrue();
    }

    @Test
    void theLifetimesAreThirtyMinutesForResetAndTwentyFourHoursForActivation() {
        assertThat(CredentialTokenType.PASSWORD_RESET.lifetime()).hasMinutes(30);
        assertThat(CredentialTokenType.ACTIVATION.lifetime()).hasHours(24);
    }

    @Test
    void consumptionLooksUpOnlyAWellFormedTokenUnderItsOwnType() {
        assertThat(CredentialTokenConsumption.lookup(CredentialTokenType.ACTIVATION, null)).isEmpty();
        assertThat(CredentialTokenConsumption.lookup(CredentialTokenType.ACTIVATION, "short")).isEmpty();
        assertThat(CredentialTokenConsumption.lookup(CredentialTokenType.ACTIVATION, SAMPLE))
                .contains(CredentialTokenHash.hash(CredentialTokenType.ACTIVATION, SAMPLE));
        assertThat(CredentialTokenConsumption.lookup(CredentialTokenType.PASSWORD_RESET, SAMPLE))
                .contains(CredentialTokenHash.hash(CredentialTokenType.PASSWORD_RESET, SAMPLE));
    }

    @Test
    void exactlyOneChangedRowIsARedemption() {
        assertThat(CredentialTokenConsumption.redeemed(0)).isFalse();
        assertThat(CredentialTokenConsumption.redeemed(1)).isTrue();
        assertThatThrownBy(() -> CredentialTokenConsumption.redeemed(2)).isInstanceOf(IllegalStateException.class);
    }
}
