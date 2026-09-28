package sg.securedhello.security.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;

import org.junit.jupiter.api.Test;

import sg.securedhello.audit.LogFieldHasher;
import sg.securedhello.testsupport.Proves;

/**
 * {@code source.ip_hash} is pinned end to end, from address to hash (ADR-054): HMAC-SHA-256 under the log key over
 * {@code "ip:" || source key}. The expected values were computed independently of the application code.
 *
 * <p>IPv4 hashes changed when the input moved from the dotted quad to the source key: {@code 203.0.113.7} used to hash
 * {@code "ip:203.0.113.7"} and now hashes {@code "ip:4:cb007107"}, so no hash computed the older way matches a current
 * one (R-AUD-021). Changing the IPv6 prefix length changes every IPv6 hash the same way.
 */
class SourceIpHashTest {

    /** A fixed {@code app.security.hmac.log.key}: bytes 0x00 to 0x1f. */
    private static final LogFieldHasher HASHER = new LogFieldHasher(HexFormat.of()
            .parseHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"));

    @Test
    @Proves("T-AUD-041")
    void anIpv4AddressHashesItsFamilyTaggedSourceKey() {
        assertThat(hash("203.0.113.7"))
                .isEqualTo("51574cb8386d5f7ac65bb737926a7ae14a14bb03fd2a84a96f5caf7dc8ed8771")
                .isEqualTo(hash("::ffff:203.0.113.7"))
                .isNotEqualTo("ee6f9612bd55af19581fb91675a14447ba26035f8c4a528dfbb840166be10c84");
    }

    @Test
    @Proves("T-AUD-041")
    void anIpv6AddressHashesItsSlash64SourceKey() {
        assertThat(hash("2001:db8:1:2::1"))
                .isEqualTo("84c79b497bf014d6a148bb1c876fdf2b2af8e906045edaa322cadb602e57eb94")
                .isEqualTo(hash("2001:db8:1:2:aaaa:bbbb:cccc:dddd"));
    }

    @Test
    @Proves("T-AUD-041")
    void anUnparseableTokenHashesTheSharedConstantKey() {
        assertThat(hash("not-an-address"))
                .isEqualTo("103d1f5189d6bc4717334a5625aa825c46590d8faa589ed129ee7c869dede58b");
    }

    private static String hash(String address) {
        return HASHER.sourceIpHash(SourceKeyResolver.derive(address, 64));
    }
}
