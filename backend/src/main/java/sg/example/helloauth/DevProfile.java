package sg.example.helloauth;

import org.springframework.core.env.Environment;

/**
 * The dev profile, the only one allowed development conveniences: a session cookie without
 * {@code Secure}, the H2 console, and reset links written to the log.
 */
public final class DevProfile {

    public static final String NAME = "dev";

    private DevProfile() {
    }

    public static boolean isActive(Environment environment) {
        return environment.matchesProfiles(NAME);
    }
}
