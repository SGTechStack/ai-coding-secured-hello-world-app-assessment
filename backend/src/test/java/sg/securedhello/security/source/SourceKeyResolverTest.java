package sg.securedhello.security.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import sg.securedhello.security.source.ClientIpProperties.Source;
import sg.securedhello.testsupport.MutableClock;
import sg.securedhello.testsupport.Proves;

/** The pure source-key derivation (ADR-020) and the resolver's handling of unparseable addresses (R-RL-020). */
class SourceKeyResolverTest {

    private static final Duration LOG_WINDOW = Duration.ofMinutes(15);

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final MutableClock clock = MutableClock.startingNow();
    private final Logger logger = (Logger) LoggerFactory.getLogger(SourceKeyResolver.class);
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void captureLog() {
        logged.start();
        logger.addAppender(logged);
    }

    @AfterEach
    void releaseLog() {
        logger.detachAppender(logged);
    }

    @Test
    @Proves({"T-RL-026"})
    void ipv4KeysOnTheWholeAddress() {
        assertThat(key("203.0.113.7")).isEqualTo("4:cb007107");
        assertThat(key("0.0.0.0")).isEqualTo("4:00000000");
        assertThat(key("255.255.255.255")).isEqualTo("4:ffffffff");
        assertThat(key("203.0.113.7")).isNotEqualTo(key("203.0.113.8"));
    }

    @Test
    @Proves({"T-RL-026"})
    void twoAddressesInOneSlash64GiveOneKeyAndTwoSlash64sGiveTwo() {
        assertThat(key("2001:db8:1:2:aaaa:bbbb:cccc:dddd")).isEqualTo(key("2001:db8:1:2::1"))
                .isEqualTo("6:20010db8000100020000000000000000/64");
        assertThat(key("2001:db8:1:2::1")).isNotEqualTo(key("2001:db8:1:3::1"));
    }

    @Test
    @Proves({"T-RL-026"})
    void spellingsOfOneAddressGiveOneKey() {
        String expected = key("2001:db8::1");
        assertThat(List.of("2001:0db8:0000:0000:0000:0000:0000:0001", "2001:DB8::1", "2001:db8:0:0::1",
                "2001:db8::1%eth0", "2001:db8::1%7"))
                .allSatisfy(spelling -> assertThat(key(spelling)).isEqualTo(expected));
    }

    @Test
    @Proves({"T-RL-026"})
    void ipv4MappedIpv6KeysAsItsIpv4Address() {
        assertThat(key("::ffff:203.0.113.7")).isEqualTo(key("203.0.113.7"));
        assertThat(key("::FFFF:cb00:7107")).isEqualTo("4:cb007107");
    }

    @Test
    @Proves({"T-RL-026"})
    void ipv4AndIpv6KeysAreTaggedByFamily() {
        assertThat(key("::cb00:7107")).startsWith("6:");
        assertThat(key("203.0.113.7")).startsWith("4:");
    }

    @ParameterizedTest
    @CsvSource({
            "48,  6:20010db8abcd00000000000000000000/48",
            "52,  6:20010db8abcd10000000000000000000/52",
            "56,  6:20010db8abcd12000000000000000000/56",
            "64,  6:20010db8abcd12340000000000000000/64",
            "100, 6:20010db8abcd12345555666670000000/100",
            "127, 6:20010db8abcd1234555566667777fffe/127",
            "128, 6:20010db8abcd1234555566667777ffff/128"})
    @Proves({"T-RL-026", "T-RL-027"})
    void ipv6IsMaskedToTheConfiguredPrefix(int prefix, String expected) {
        assertThat(SourceKeyResolver.derive("2001:db8:abcd:1234:5555:6666:7777:ffff", prefix).value())
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "localhost", "bad.cafe", "untrusted-proxy", "[2001:db8::1]", "2001:db8::g",
            "1.2.3", "1.2.3.4.5", "256.1.1.1", "01.2.3.4", "1.2.3.4 ", ".::1", "12345::1", "1:2:3:4:5:6:7:8:9",
            "::1::", "%eth0"})
    @Proves("T-RL-025")
    void nonLiteralsAreUnparseable(String token) {
        assertThat(SourceKeyResolver.derive(token, 64)).isSameAs(SourceKey.UNPARSEABLE);
    }

    @Test
    void aMissingAddressIsUnparseable() {
        assertThat(SourceKeyResolver.derive(null, 64)).isSameAs(SourceKey.UNPARSEABLE);
    }

    @Test
    void theResolverKeysTheRequestsClientAddressAtTheConfiguredPrefix() {
        SourceKeyResolver resolver = resolver(Source.SOCKET, 56);
        logged.list.clear();

        assertThat(resolver.resolve(request("2001:db8:1:2ff::1")).value())
                .isEqualTo("6:20010db8000102000000000000000000/56");
        assertThat(resolver.resolve(request("203.0.113.7")).value()).isEqualTo("4:cb007107");
        assertThat(meterRegistry.counter(SourceKeyResolver.UNPARSEABLE_COUNTER).count()).isZero();
        assertThat(logged.list).isEmpty();
    }

    @Test
    void theStartupLogStatesTheEffectivePrefixLength() {
        resolver(Source.PROXY, 56);

        assertThat(logged.list).singleElement().extracting(ILoggingEvent::getFormattedMessage)
                .isEqualTo("Source keys: IPv4 /32, IPv6 prefix length 56; client address from PROXY");
    }

    @Test
    void anUnparseableProxiedAddressCountsAndWarnsWithTheTokenEscaped() {
        SourceKeyResolver resolver = resolver(Source.PROXY, 64);
        logged.list.clear();

        assertThat(resolver.resolve(request("evil\nhost\\x"))).isSameAs(SourceKey.UNPARSEABLE);

        assertThat(meterRegistry.counter(SourceKeyResolver.UNPARSEABLE_COUNTER).count()).isEqualTo(1);
        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
            assertThat(event.getFormattedMessage())
                    .isEqualTo("Client address 'evil\\u000ahost\\u005cx' is not an IP literal; keyed as unparseable");
        });
    }

    @Test
    void anUnparseableSocketAddressIsAnError() {
        SourceKeyResolver resolver = resolver(Source.SOCKET, 64);
        logged.list.clear();

        resolver.resolve(request("not-an-address"));

        assertThat(logged.list).singleElement().extracting(ILoggingEvent::getLevel)
                .isEqualTo(ch.qos.logback.classic.Level.ERROR);
    }

    @Test
    void theUnparseableLineIsWrittenOncePerWindowWhileTheCounterCountsEveryRequest() {
        SourceKeyResolver resolver = resolver(Source.PROXY, 64);
        logged.list.clear();

        resolver.resolve(request("first-token"));
        clock.advance(LOG_WINDOW.minusMillis(1));
        resolver.resolve(request("second-token"));
        assertThat(logged.list).singleElement().extracting(ILoggingEvent::getFormattedMessage).asString()
                .contains("first-token");

        clock.advance(Duration.ofMillis(1));
        resolver.resolve(request("third-token"));

        assertThat(meterRegistry.counter(SourceKeyResolver.UNPARSEABLE_COUNTER).count()).isEqualTo(3);
        assertThat(logged.list).extracting(ILoggingEvent::getFormattedMessage).hasSize(2).last().asString()
                .contains("third-token");
    }

    @Test
    void theKeyIsDerivedOncePerRequestAndKeptOnIt() {
        SourceKeyResolver resolver = resolver(Source.SOCKET, 64);
        MockHttpServletRequest request = request("not-an-address");
        logged.list.clear();

        SourceKey first = resolver.resolve(request);
        assertThat(resolver.resolve(request)).isSameAs(first);
        assertThat(request.getAttribute(SourceKeyResolver.REQUEST_ATTRIBUTE)).isSameAs(SourceKey.UNPARSEABLE);
        assertThat(meterRegistry.counter(SourceKeyResolver.UNPARSEABLE_COUNTER).count()).isEqualTo(1);
    }

    @Test
    void theLoggedTokenIsCutToSixtyFourCharacters() {
        assertThat(SourceKeyResolver.loggable("a".repeat(63) + "bc")).isEqualTo("a".repeat(63) + "b");
        assertThat(SourceKeyResolver.loggable("a".repeat(64))).isEqualTo("a".repeat(64));
        assertThat(SourceKeyResolver.loggable("a".repeat(62) + "é")).isEqualTo("a".repeat(62) + "\\u");
        assertThat(SourceKeyResolver.loggable(" ~\u001f\u007f")).isEqualTo(" ~\\u001f\\u007f");
        assertThat(SourceKeyResolver.loggable(null)).isEqualTo("null");
    }

    private static String key(String token) {
        return SourceKeyResolver.derive(token, 64).value();
    }

    private SourceKeyResolver resolver(Source source, int prefix) {
        return new SourceKeyResolver(new ClientIpProperties(source, List.of("192.0.2.1"), prefix), meterRegistry,
                clock, LOG_WINDOW);
    }

    private static MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
