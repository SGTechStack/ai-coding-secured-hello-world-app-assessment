package sg.securedhello.security.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import sg.securedhello.SecuredHelloApplication;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.TemporaryH2FileInitializer;

/**
 * {@code X-Forwarded-For} through real Tomcat (level P): ignored from an untrusted peer, honoured from a named trusted
 * proxy (REJ-015; R-RL-007). Each case needs its own {@code client-ip} configuration, and that decides what Tomcat
 * installs, so each runs the application on its own random port rather than widening a shared context; the loopback
 * test client plays the proxy.
 */
class ForwardedForOnARealPortTest {

    private static final String CLIENT = "203.0.113.7";
    private static final String LOOPBACK_KEY = "4:7f000001";
    /** How the resolver writes a backslash: its escape, six characters. */
    private static final String ESCAPED_BACKSLASH = "\\" + "u005c";

    @Test
    @Proves("T-CFG-001")
    void theDefaultSocketSourceIgnoresForwardedFor() throws Exception {
        assertThat(sourceKeyWith(CLIENT)).isEqualTo(LOOPBACK_KEY);
    }

    @Test
    void aProxySourceIgnoresForwardedForFromAPeerItDoesNotName() throws Exception {
        assertThat(sourceKeyWith(CLIENT, "app.security.client-ip.source=proxy",
                "app.security.client-ip.trusted-proxies=192.0.2.1,2001:db8::1")).isEqualTo(LOOPBACK_KEY);
    }

    @Test
    void aProxySourceHonoursForwardedForFromANamedProxy() throws Exception {
        String[] trustLoopback = {"app.security.client-ip.source=proxy",
                "app.security.client-ip.trusted-proxies=192.0.2.1,127.0.0.1"};

        assertThat(sourceKeyWith(CLIENT, trustLoopback)).isEqualTo("4:cb007107");
        assertThat(sourceKeyWith("198.51.100.1, 2001:db8:1:2::99", trustLoopback))
                .isEqualTo("6:20010db8000100020000000000000000/64");
    }

    @Test
    @Proves("T-RL-020")
    void unparseableForwardedTokensShareOneBucketCountEachTimeAndWarnOncePerWindow() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(SourceKeyResolver.class);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        try (ConfigurableApplicationContext context = start("app.security.client-ip.source=proxy",
                "app.security.client-ip.trusted-proxies=127.0.0.1")) {
            logged.start();
            logger.addAppender(logged);
            try {
                int port = ((WebServerApplicationContext) context).getWebServer().getPort();
                send(port, "evil\\" + "a".repeat(100));
                send(port, "another-bad-token");

                assertThat(context.getBean(SourceKeyProbe.class).observed.get()).isSameAs(SourceKey.UNPARSEABLE);
                assertThat(context.getBean(MeterRegistry.class).counter(SourceKeyResolver.UNPARSEABLE_COUNTER)
                        .count()).isEqualTo(2);
                assertThat(logged.list).singleElement().satisfies(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getFormattedMessage()).isEqualTo("Client address '"
                            + ("evil" + ESCAPED_BACKSLASH + "a".repeat(100))
                                    .substring(0, SourceKeyResolver.LOGGED_TOKEN_LIMIT)
                            + "' is not an IP literal; keyed as unparseable");
                });
            } finally {
                logger.detachAppender(logged);
            }
        }
    }

    private static ConfigurableApplicationContext start(String... properties) {
        return new SpringApplicationBuilder(SecuredHelloApplication.class, SourceKeyProbe.class)
                .profiles("dev")
                .initializers(new TemporaryH2FileInitializer())
                .run(arguments(properties));
    }

    private static void send(int port, String forwardedFor) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health"))
                    .header(ClientIpConfig.FORWARDED_FOR, forwardedFor).build(), BodyHandlers.discarding());
        }
    }

    /** Starts the application with {@code properties}, sends one request forwarded for {@code forwardedFor}. */
    private static String sourceKeyWith(String forwardedFor, String... properties) throws IOException,
            InterruptedException {
        try (ConfigurableApplicationContext context = start(properties)) {
            send(((WebServerApplicationContext) context).getWebServer().getPort(), forwardedFor);
            return context.getBean(SourceKeyProbe.class).observed.get().value();
        }
    }

    /** Command-line arguments, so they outrank {@code application.yml} (builder properties would not). */
    private static String[] arguments(String... properties) {
        return Stream.concat(Stream.of("server.port=0", "app.security.password.bcrypt-strength=4"),
                Stream.of(properties)).map(property -> "--" + property).toArray(String[]::new);
    }

    /**
     * Records the source key the application's resolver derives for the request, ahead of every other filter. Not a
     * {@code @Configuration}, so component scanning never picks it up; it is registered only as an explicit source.
     */
    static class SourceKeyProbe {

        final AtomicReference<SourceKey> observed = new AtomicReference<>();

        @Bean
        FilterRegistrationBean<Filter> sourceKeyProbeFilter(SourceKeyResolver resolver) {
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>((request, response, chain) -> {
                observed.set(resolver.resolve((HttpServletRequest) request));
                chain.doFilter(request, response);
            });
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }
}
