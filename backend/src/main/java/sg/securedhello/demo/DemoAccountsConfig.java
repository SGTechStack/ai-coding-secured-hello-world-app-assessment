package sg.securedhello.demo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Binds the demo accounts' values under {@code dev} only; no other profile has them. */
@Configuration(proxyBeanMethods = false)
@Profile("dev")
@EnableConfigurationProperties(DemoAccountsProperties.class)
class DemoAccountsConfig {
}
