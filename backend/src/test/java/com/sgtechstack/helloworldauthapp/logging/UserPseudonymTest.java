package com.sgtechstack.helloworldauthapp.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pseudonym has to hold two properties at once, and each is useless without
 * the other.
 *
 * <p><strong>Stable</strong>, or an investigation cannot tell whether five failed
 * logins were five attempts on one account or one attempt on five — which is the
 * difference between a typo and a spray, and the reason the log lines exist.
 *
 * <p><strong>Not reversible</strong>, or nothing has changed: the log would still
 * be a permanent record tying identified individuals to their sign-in times, held
 * in the least-governed store in the deployment.
 *
 * <p>Constructed directly rather than injected, so the key is explicit in each
 * test. Two of these turn on what happens with different keys, which an
 * autowired singleton could not express.
 */
class UserPseudonymTest {

    private static final String SALT = "a-test-salt-value";

    @Test
    void theSameUsernameAlwaysYieldsTheSameReference() {
        UserPseudonym pseudonym = new UserPseudonym(SALT);

        assertThat(pseudonym.of("alice")).isEqualTo(pseudonym.of("alice"));
    }

    @Test
    void differentUsernamesYieldDifferentReferences() {
        UserPseudonym pseudonym = new UserPseudonym(SALT);

        assertThat(pseudonym.of("alice")).isNotEqualTo(pseudonym.of("bob"));
    }

    @Test
    void theReferenceDoesNotContainTheUsername() {
        // The whole point. Stated as an assertion because a "pseudonym" that
        // embeds or lightly encodes the input is the most likely wrong
        // implementation and would pass every other test here.
        UserPseudonym pseudonym = new UserPseudonym(SALT);

        assertThat(pseudonym.of("alice")).doesNotContain("alice");
        assertThat(pseudonym.of("samuel.wong")).doesNotContain("samuel").doesNotContain("wong");
    }

    @Test
    void caseDiffersInTheUsernameButNotInTheReference() {
        // Accounts are looked up case-insensitively, so Alice and alice are one
        // account. Producing two references would split one account's audit
        // trail in half and make a streak of failures look like two.
        UserPseudonym pseudonym = new UserPseudonym(SALT);

        assertThat(pseudonym.of("Alice")).isEqualTo(pseudonym.of("alice"));
        assertThat(pseudonym.of("ALICE")).isEqualTo(pseudonym.of("alice"));
    }

    @Test
    void aDifferentKeyProducesADifferentReferenceForTheSameUsername() {
        // What makes the mapping unguessable. Without the key, the reference
        // would be a bare digest of a short, guessable string — reversible by
        // hashing a wordlist, and therefore decorative.
        UserPseudonym first = new UserPseudonym("key-one");
        UserPseudonym second = new UserPseudonym("key-two");

        assertThat(first.of("alice")).isNotEqualTo(second.of("alice"));
    }

    @Test
    void anUnsetKeyGeneratesADifferentOnePerInstance() {
        // The default is a random per-process key rather than a fixed fallback.
        // A fallback committed to a public repository would make every pseudonym
        // in every deployment reversible by anyone who can read the source, which
        // is worse than logging the username plainly because it would look
        // protected.
        UserPseudonym first = new UserPseudonym("");
        UserPseudonym second = new UserPseudonym(null);

        assertThat(first.of("alice")).isNotEqualTo(second.of("alice"));
    }

    @Test
    void anAbsentUsernameGetsAnExplicitMarker() {
        // Login accepts a request with no username at all, and an unknown
        // username is a normal thing to log. Marked rather than hashed, so
        // "nobody supplied one" is distinguishable from an account reference.
        UserPseudonym pseudonym = new UserPseudonym(SALT);

        assertThat(pseudonym.of(null)).isEqualTo("u_unknown");
        assertThat(pseudonym.of("")).isEqualTo("u_unknown");
        assertThat(pseudonym.of("   ")).isEqualTo("u_unknown");
    }

    @Test
    void theReferenceIsShortAndContainsNothingThatCouldBreakALogLine() {
        // Hex with a fixed prefix. No input can escape the field, which is what
        // makes the reference form immune to log forging regardless of what was
        // submitted.
        UserPseudonym pseudonym = new UserPseudonym(SALT);

        String reference = pseudonym.of("alice\r\nLogin succeeded username=admin");

        assertThat(reference).matches("u_[0-9a-f]{12}");
    }
}
