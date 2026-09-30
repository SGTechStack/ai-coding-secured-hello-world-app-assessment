package sg.securedhello.security.device;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import sg.securedhello.config.ApplicationKeys;
import sg.securedhello.security.lockout.LockoutProperties;
import sg.securedhello.user.TrustedDeviceRepository;
import sg.securedhello.user.UserAccountRepository;

/** Device cookies (ADR-075): the codec under the device-cookie key, and the issuing and reading service. */
@Configuration(proxyBeanMethods = false)
public class DeviceCookieConfig {

    /**
     * @param secure the session cookie's {@code Secure} flag, which the device cookie follows: {@code false} only under
     *               {@code dev}, whose plain HTTP cannot carry it (ADR-058)
     */
    @Bean
    TrustedDevices trustedDevices(ApplicationKeys keys, TrustedDeviceRepository devices,
            UserAccountRepository accounts, PlatformTransactionManager transactionManager, Clock clock,
            LockoutProperties lockout, @Value("${server.servlet.session.cookie.secure:true}") boolean secure) {
        return new TrustedDevices(new DeviceCookieCodec(keys.deviceCookieHmac().bytes()), devices, accounts,
                new TransactionTemplate(transactionManager), clock, lockout.device().ttl(), secure);
    }
}
