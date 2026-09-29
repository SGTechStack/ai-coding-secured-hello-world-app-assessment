package sg.securedhello.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import sg.securedhello.SecuredHelloApplication;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.TemporaryH2FileInitializer;

/**
 * The session cookie's name and {@code Secure} flag per profile (ADR-058; REJ-061). The {@code __Host-} prefix needs
 * {@code Secure}, {@code Path=/} and no {@code Domain}, which plain-HTTP development cannot satisfy, so {@code dev}
 * uses {@code SESSION}. Each case boots the whole application on its own temporary H2 file.
 */
class SessionCookieProfileTest {

    /** Boots with {@code profiles}, fetches a CSRF token and returns the one raw {@code Set-Cookie}. */
    private static String sessionCookie(String... profiles) throws Exception {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(SecuredHelloApplication.class)
                .profiles(profiles)
                .initializers(new TemporaryH2FileInitializer())
                .run("--server.port=0")) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            try (HttpClient client = HttpClient.newHttpClient()) {
                List<String> setCookies = client.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/csrf")).build(), BodyHandlers.discarding())
                        .headers().allValues("Set-Cookie");
                assertThat(setCookies).hasSize(1);
                return setCookies.getFirst();
            }
        }
    }

    private static List<String> attributes(String setCookie) {
        return List.of(setCookie.split(";\\s*"));
    }

    private static List<String> attributeNames(String setCookie) {
        return attributes(setCookie).stream().skip(1).map(a -> a.split("=", 2)[0].toLowerCase(Locale.ROOT)).toList();
    }

    @Test
    @Proves("T-SES-011")
    void outsideDevTheCookieIsHostPrefixedAndSecure() throws Exception {
        String setCookie = sessionCookie();

        assertThat(attributes(setCookie).getFirst()).startsWith("__Host-SESSION=");
        assertThat(attributes(setCookie)).contains("Secure", "HttpOnly", "SameSite=Strict", "Path=/");
        assertThat(attributeNames(setCookie)).doesNotContain("domain", "max-age", "expires");
    }

    @Test
    @Proves("T-SES-011")
    void inDevTheCookieIsSessionAndNotSecure() throws Exception {
        String setCookie = sessionCookie("dev");

        assertThat(attributes(setCookie).getFirst()).startsWith("SESSION=");
        assertThat(attributes(setCookie)).contains("HttpOnly", "SameSite=Strict", "Path=/");
        assertThat(attributeNames(setCookie)).doesNotContain("secure", "domain", "max-age", "expires");
    }
}
