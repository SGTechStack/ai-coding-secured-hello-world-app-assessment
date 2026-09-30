package sg.example.helloauth.account;

import java.time.Duration;

/**
 * When repeated failed logins lock an Account.
 *
 * @param threshold consecutive failed logins that lock the Account
 * @param duration how long the lock lasts before it lifts by itself
 */
public record LockoutPolicy(int threshold, Duration duration) {
}
