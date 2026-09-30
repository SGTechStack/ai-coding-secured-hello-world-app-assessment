package sg.securedhello;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

import sg.securedhello.recovery.RecoveryLauncher;

/**
 * The Secured Hello World REST API.
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded so Boot never creates its in-memory user or logs a
 * generated password.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SecuredHelloApplication {

    /** Starts the application, or, with {@code --rebind}, the offline recovery runner instead (ADR-072). */
    public static void main(String[] args) {
        if (RecoveryLauncher.requested(args)) {
            System.exit(RecoveryLauncher.run(args, System.in, System.out, System.err));
        }
        SpringApplication.run(SecuredHelloApplication.class, args);
    }
}
