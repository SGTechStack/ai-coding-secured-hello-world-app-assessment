package sg.securedhello.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Control 2 of ADR-057: once the application is ready, refuses to run outside {@code dev} if the dev-only reset-link
 * logger would emit. It reads the effective level through Boot's {@link LoggingSystem}, so it catches every path the
 * refresh-phase validator cannot see: environment variables, {@code SPRING_APPLICATION_JSON} and parent loggers.
 */
@Component
public class ResetLinkLoggerGuard implements ApplicationListener<ApplicationReadyEvent> {

    /** The dev-only logger the stubbed email service writes reset links to (ADR-057). */
    public static final String LOGGER_NAME = "sg.securedhello.email.ResetLinkLogger";

    /** The level reset links are written at; the logger emits nothing unless it is enabled at this level. */
    public static final LogLevel LINK_LEVEL = LogLevel.DEBUG;

    /** The property that sets the logger's level. */
    static final String LEVEL_PROPERTY = "logging.level." + LOGGER_NAME;

    private final LoggingSystem loggingSystem;
    private final Environment environment;

    ResetLinkLoggerGuard(LoggingSystem loggingSystem, Environment environment) {
        this.loggingSystem = loggingSystem;
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (environment.matchesProfiles(ProhibitedConfigurationValidator.DEV)) {
            return;
        }
        LoggerConfiguration configuration = loggingSystem.getLoggerConfiguration(LOGGER_NAME);
        if (configuration != null && emits(configuration.getEffectiveLevel())) {
            throw new IllegalStateException("Startup refused: logger " + LOGGER_NAME + " is enabled at "
                    + configuration.getEffectiveLevel() + " outside the dev profile and would log reset links "
                    + "(ADR-057)");
        }
    }

    private static boolean emits(LogLevel effective) {
        return effective != null && effective.ordinal() <= LINK_LEVEL.ordinal();
    }
}
