package sg.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

import sg.securedhello.security.source.SourceKey;
import sg.securedhello.testsupport.Proves;

/** {@code session.hash} is keyed (ADR-054): with an unkeyed hash, anyone holding the cookie could find its rows. */
class LogFieldHasherTest {

    private static final LogFieldHasher HASHER = new LogFieldHasher(HexFormat.of()
            .parseHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"));

    @Test
    @Proves("T-AUD-042")
    void theSessionHashIsTheDomainPrefixedHmacNotTheUnkeyedDigest() throws Exception {
        String unkeyed = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("session-id-4e7a1c9b".getBytes(StandardCharsets.UTF_8)));

        assertThat(HASHER.sessionHash("session-id-4e7a1c9b"))
                .isEqualTo("09ccb09bb8894783cf25f348f6bb5bad1e8643b1983f5dff346fb5562bc9ca55")
                .isNotEqualTo(unkeyed);
    }

    @Test
    void anotherKeyGivesAnotherHash() {
        LogFieldHasher other = new LogFieldHasher(new byte[32]);

        assertThat(other.sessionHash("session-id-4e7a1c9b")).isNotEqualTo(HASHER.sessionHash("session-id-4e7a1c9b"));
        assertThat(other.sourceIpHash(SourceKey.UNPARSEABLE)).isNotEqualTo(HASHER.sourceIpHash(SourceKey.UNPARSEABLE));
    }

    @Test
    void theDomainPrefixSeparatesASessionIdFromASourceKeyOfTheSameText() {
        assertThat(HASHER.sessionHash("4:cb007107")).isNotEqualTo(HASHER.sourceIpHash(new SourceKey("4:cb007107")));
    }

    @Test
    void theKeyNeverAppearsInItsTextForm() {
        assertThat(HASHER).hasToString("LogFieldHasher[key=<redacted>]");
    }
}
