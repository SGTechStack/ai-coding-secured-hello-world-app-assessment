package com.sgtechstack.helloworldauthapp.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the scheduler that drives retention sweeps.
 *
 * <p>Kept off the application class and behind a property so that it can be
 * switched off wholesale. The test suite does exactly that: a purge firing on
 * its own timetable inside an integration test is a background thread deleting
 * rows another test is asserting on, which produces failures that reproduce
 * roughly one run in twenty and get dismissed as flakes. Test configuration sets
 * {@code app.retention.purge-enabled=false} and the purge is exercised by
 * calling it directly instead — the behaviour under test is which rows it
 * removes, not whether Spring's scheduler fires.
 *
 * <p>{@code matchIfMissing = true} keeps the production default on, so
 * forgetting the property leaves retention enforced rather than silently
 * disabled.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.retention.purge-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
