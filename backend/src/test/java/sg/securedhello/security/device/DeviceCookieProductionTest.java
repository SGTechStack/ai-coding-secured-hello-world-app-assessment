package sg.securedhello.security.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;

import sg.securedhello.config.ApplicationKeys;
import sg.securedhello.config.KeyMaterial;
import sg.securedhello.security.lockout.LockoutProperties;
import sg.securedhello.testsupport.CtxNondevTest;
import sg.securedhello.time.ClockConfig;
import sg.securedhello.user.TrustedDevice;
import sg.securedhello.user.TrustedDeviceRepository;
import sg.securedhello.user.UserAccount;
import sg.securedhello.user.UserAccountRepository;

/**
 * The device cookie as production issues it ({@code ctx-nondev}; ADR-058; ADR-075): the production configuration, with
 * no profile, wires {@link DeviceCookieConfig} and its cookie is {@code __Host-DEVICE}, {@code Secure},
 * {@code HttpOnly}, {@code SameSite=Strict}, {@code Path=/}, host-only and lives for the production lifetime. The shared
 * contexts run under {@code dev}, whose plain-HTTP {@code DEVICE} cookie cannot carry {@code Secure}. Persistence and
 * the key are stubbed: the cookie's attributes are the control here.
 */
class DeviceCookieProductionTest extends CtxNondevTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LockoutProperties.class)
    static class Lockout {
    }

    @Test
    void outsideDevTheDeviceCookieIsHostPrefixedSecureAndLivesThirtyDays() {
        KeyMaterial key = mock(KeyMaterial.class);
        when(key.bytes()).thenReturn(new byte[KeyMaterial.LENGTH]);
        ApplicationKeys keys = mock(ApplicationKeys.class);
        when(keys.deviceCookieHmac()).thenReturn(key);
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        when(accounts.findForUpdateById(any())).thenReturn(Optional.of(mock(UserAccount.class)));
        TrustedDeviceRepository devices = mock(TrustedDeviceRepository.class);
        TrustedDevice saved = mock(TrustedDevice.class);
        when(saved.getId()).thenReturn(UUID.randomUUID());
        when(devices.save(any())).thenReturn(saved);

        productionContextRunner()
                .withUserConfiguration(DeviceCookieConfig.class, Lockout.class, ClockConfig.class)
                .withBean(ApplicationKeys.class, () -> keys)
                .withBean(UserAccountRepository.class, () -> accounts)
                .withBean(TrustedDeviceRepository.class, () -> devices)
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    TrustedDevices trusted = context.getBean(TrustedDevices.class);
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    trusted.issue(UUID.randomUUID(), response);

                    assertThat(trusted.cookieName()).isEqualTo(TrustedDevices.SECURE_COOKIE);
                    List<String> attributes = List.of(response.getHeader(HttpHeaders.SET_COOKIE).split(";\\s*"));
                    assertThat(attributes.getFirst()).startsWith("__Host-DEVICE=");
                    assertThat(attributes).contains("Secure", "HttpOnly", "SameSite=Strict", "Path=/",
                            "Max-Age=" + Duration.ofDays(30).toSeconds());
                    assertThat(attributes).map(attribute -> attribute.toLowerCase(Locale.ROOT))
                            .noneMatch(attribute -> attribute.startsWith("domain"));
                    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).hasSize(1);
                });
    }
}
