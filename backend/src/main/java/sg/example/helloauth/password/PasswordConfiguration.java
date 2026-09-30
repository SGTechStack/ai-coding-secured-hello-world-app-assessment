package sg.example.helloauth.password;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.security.web.authentication.password.HaveIBeenPwnedRestApiPasswordChecker;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class PasswordConfiguration {

    /**
     * Spring Security's checker against the Have I Been Pwned range API: only the first five hex
     * characters of the password's SHA-1 leave the app (k-anonymity). On its own it treats a
     * failed call as "not compromised"; this client instead throws
     * {@link CompromisedPasswordCheckUnavailableException}, which the checker doesn't catch, so
     * an outage never lets a password through. Spring Security also uses this bean at login.
     */
    @Bean
    CompromisedPasswordChecker compromisedPasswordChecker(CompromisedPasswordCheckProperties properties) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(properties.timeout());
        requests.setReadTimeout(properties.timeout());
        RestClient client = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requests)
                .requestInterceptor((request, body, execution) -> {
                    try {
                        return execution.execute(request, body);
                    } catch (IOException ex) {
                        throw new CompromisedPasswordCheckUnavailableException("Pwned Passwords API unreachable", ex);
                    }
                })
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new CompromisedPasswordCheckUnavailableException(
                            "Pwned Passwords API answered " + response.getStatusCode().value(), null);
                })
                .build();
        HaveIBeenPwnedRestApiPasswordChecker checker = new HaveIBeenPwnedRestApiPasswordChecker();
        checker.setRestClient(client);
        return checker;
    }
}
