package sg.securedhello.security.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.securedhello.testsupport.Proves;

/** Source-key parsing never reaches DNS, proven with a failing resolver provider installed (ADR-020). */
class SourceKeyNoDnsTest {

    @Test
    void theTrapCatchesARealLookup() {
        assertThat(DnsTrap.lookupsDuring(() -> assertThatExceptionOfType(UnknownHostException.class)
                .isThrownBy(() -> InetAddress.getByName("bad.cafe")))).isEqualTo(1);
    }

    @Test
    @Proves("T-RL-025")
    void parsingNeverCallsTheResolver() {
        List<String> hostileOrOdd = List.of("localhost", "bad.cafe", "untrusted-proxy", "example.com", "[::1]",
                "[2001:db8::1]", "cafe", "beef.dead", "g::1", ".::1", "1.2.3", "01.2.3.4", "fe80::1%eth0",
                "::1%1", "203.0.113.7%eth0", "::ffff:203.0.113.7", "2001:db8::1", "203.0.113.7");

        int lookups = DnsTrap.lookupsDuring(() -> {
            assertThat(SourceKeyResolver.derive("localhost", 64)).isSameAs(SourceKey.UNPARSEABLE);
            assertThat(SourceKeyResolver.derive("bad.cafe", 64)).isSameAs(SourceKey.UNPARSEABLE);
            assertThat(SourceKeyResolver.derive("untrusted-proxy", 64)).isSameAs(SourceKey.UNPARSEABLE);
            assertThat(SourceKeyResolver.derive("[2001:db8::1]", 64)).isSameAs(SourceKey.UNPARSEABLE);
            assertThat(SourceKeyResolver.derive("fe80::1%eth0", 64).value())
                    .isEqualTo("6:fe800000000000000000000000000000/64");
            hostileOrOdd.forEach(token -> SourceKeyResolver.derive(token, 64));
        });

        assertThat(lookups).isZero();
    }
}
