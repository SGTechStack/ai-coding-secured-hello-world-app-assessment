package sg.securedhello;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * The Secured Hello World REST API.
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded so Boot never creates its in-memory user or logs a
 * generated password.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SecuredHelloApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecuredHelloApplication.class, args);
    }
}
