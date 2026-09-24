package com.sgtechstack.helloworldauthapp.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The CORS allow-list is the load-bearing control for the whole cookie-auth
 * design: {@code allowCredentials} is true, so any origin on this list can make
 * authenticated requests with a visitor's session and read the responses. It
 * arrives from an environment variable, so a typo, a stray wildcard, or a value
 * widened during debugging and never reverted silently removes that protection.
 *
 * <p>These tests pin the startup validation that turns such a mistake into a
 * refusal to boot. Constructed directly rather than through a Spring context so
 * each bad value can be exercised without standing up an application per case.
 */
class CorsOriginValidationTest {

    private static CorsConfig construct(List<String> origins, boolean requireHttps) {
        return new CorsConfig(origins, properties(requireHttps));
    }

    /**
     * Only {@code requireHttps} matters here; everything else is irrelevant to
     * origin validation. Adding a component to {@link SecurityProperties} has
     * broken this call twice now, so the unused arguments are listed explicitly
     * with a comment rather than silently as a row of nulls — if it breaks
     * again, the fix is to pass a default for the new component, not to make
     * this test know about it.
     */
    private static SecurityProperties properties(boolean requireHttps) {
        return new SecurityProperties(
                null,   // roleMappings
                null,   // roleHierarchy
                null,   // urlGuards
                null,   // whitelist
                requireHttps,
                null,   // hstsMaxAge
                null,   // sessionAbsoluteTimeout
                null,   // contentSecurityPolicy
                null,   // rateLimits
                null    // maxRequestBodySize
        );
    }

    @Test
    void rejectsAWildcardOrigin() {
        assertThatThrownBy(() -> construct(List.of("*"), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wildcard");
    }

    @Test
    void rejectsAnOriginContainingAWildcard() {
        assertThatThrownBy(() -> construct(List.of("https://*.example.com"), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wildcard");
    }

    @Test
    void rejectsAnEmptyList() {
        assertThatThrownBy(() -> construct(List.of(), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsABlankEntry() {
        assertThatThrownBy(() -> construct(List.of("   "), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void rejectsSomethingThatIsNotAnAbsoluteOrigin() {
        assertThatThrownBy(() -> construct(List.of("example.com"), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("absolute origins");
    }

    @Test
    void rejectsPlaintextOriginsWhenHttpsIsRequired() {
        assertThatThrownBy(() -> construct(List.of("http://app.example.com"), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("https");
    }

    @Test
    void permitsPlaintextOriginsWhenHttpsIsNotRequired() {
        // The local-development case, and the only reason the flag exists.
        assertThatCode(() -> construct(List.of("http://localhost:3000"), false))
                .doesNotThrowAnyException();
    }

    @Test
    void permitsMultipleHttpsOrigins() {
        assertThatCode(() -> construct(List.of("https://app.example.com", "https://admin.example.com"), true))
                .doesNotThrowAnyException();
    }

    @Test
    void exposesTheValidatedOriginsToTheCorsConfiguration() {
        var source = construct(List.of("https://app.example.com"), true).corsConfigurationSource();

        assertThat(source).isNotNull();
    }
}
