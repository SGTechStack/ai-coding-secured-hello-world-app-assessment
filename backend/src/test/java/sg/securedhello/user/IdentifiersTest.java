package sg.securedhello.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Identifier canonicalisation and the username and email rules (ADR-045; REJ-021; REJ-027), level U. */
class IdentifiersTest {

    @Test
    void canonicalIsNfcThenTrimThenLowercaseAndNothingElse() {
        assertThat(Identifiers.canonical("  Café@Example.COM\t")).isEqualTo("café@example.com");
        assertThat(Identifiers.canonical("J.Smith+News@Gmail.com")).as("no dot or +tag folding")
                .isEqualTo("j.smith+news@gmail.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"alice", "bob.smith", "a_b-c", "abc", "abcdefghijklmnopqrstuvwxyz012345"})
    void aCanonicalWellFormedUnreservedUsernameIsValid(String username) {
        assertThat(Identifiers.validUsername(username)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Alice", " alice", "alice ", "café", "café", "ab",
            "abcdefghijklmnopqrstuvwxyz0123456", "bob@example.com", "semi;colon", "", "admin", "administrator"})
    void aNonCanonicalMalformedAtSignOrReservedUsernameIsInvalid(String username) {
        assertThat(Identifiers.validUsername(username)).isFalse();
    }

    @Test
    void everyReservedNameWouldOtherwiseBeAValidUsername() {
        assertThat(Identifiers.RESERVED_USERNAMES).allSatisfy(name -> {
            assertThat(Identifiers.canonical(name)).isEqualTo(name);
            assertThat(Identifiers.validUsername(name)).isFalse();
        });
    }

    @Test
    void anEmailAddressIsConvertedToItsCanonicalForm() {
        assertThat(Identifiers.canonicalEmail(" Bob@Example.COM ")).contains("bob@example.com");
        assertThat(Identifiers.canonicalEmail("a@b.co")).contains("a@b.co");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "plain", "@example.com", "bob@", "bob@example", "bob@@example.com", "bob@exa mple.com",
            "bob@example..com", "bob@.example.com"})
    void aMalformedEmailAddressHasNoCanonicalForm(String submitted) {
        assertThat(Identifiers.canonicalEmail(submitted)).isEmpty();
    }

    @Test
    void anAddressIsLimitedTo254CharactersAndItsLocalPartTo64() {
        String domain = "@" + "d".repeat(60) + ".example";
        assertThat(Identifiers.canonicalEmail("l".repeat(64) + domain)).isPresent();
        assertThat(Identifiers.canonicalEmail("l".repeat(65) + domain)).isEmpty();
        String longDomain = "@" + "d".repeat(63) + "." + "e".repeat(63) + "." + "f".repeat(63) + ".com";
        String local = "l".repeat(254 - longDomain.length());
        assertThat(Identifiers.canonicalEmail(local + longDomain)).isPresent();
        assertThat(Identifiers.canonicalEmail(local + "l" + longDomain)).isEmpty();
    }
}
