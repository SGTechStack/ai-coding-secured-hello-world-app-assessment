package sg.securedhello.session.shedding;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sg.securedhello.audit.AccountContext;
import sg.securedhello.audit.AuditContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.audit.SourceThrottleReason;

/**
 * Anonymous-session shedding (ADR-041): the one component that decides whether a new anonymous session may be
 * created, and the one cached row count the {@code h2Data} health indicator shares.
 *
 * <p>{@code N} is an unfiltered {@code COUNT(*)} of {@code SPRING_SESSION}, cached for {@link #COUNT_TTL} and
 * single-flight: every decision is serialised, so however many requests arrive as the cache expires, one of them
 * counts and the rest reuse its value (T-OBS-006). The free space is sampled with it, so a flood of token fetches
 * costs one count and one file-system read a second. A failed count or free-space read sheds.
 *
 * <p>An episode's start writes row 5 with {@code DISK_RESERVE_SHED}, and its end row 47, whatever the number of
 * refused requests (T-AUD-039). Row 5 is keyed like every row 5 (ADR-019), so it reaches the stream when its keying
 * window closes.
 */
public class AnonymousSessionShedding {

    /** How long one {@code COUNT(*)} answers for. */
    static final Duration COUNT_TTL = Duration.ofSeconds(1);

    private static final Logger log = LoggerFactory.getLogger(AnonymousSessionShedding.class);

    /** The live session-row count. */
    @FunctionalInterface
    interface RowCount {

        long count();
    }

    private final RowCount rows;
    private final SessionStoreVolume volume;
    private final Clock clock;
    private final AuditEmitter audit;
    private final ShedEpisode episode;

    private @Nullable Instant sampledAt;
    private long lastCount;
    private long lastFree;

    AnonymousSessionShedding(RowCount rows, SessionStoreVolume volume, Clock clock, AuditEmitter audit, long floor) {
        this.rows = rows;
        this.volume = volume;
        this.clock = clock;
        this.audit = audit;
        this.episode = new ShedEpisode(floor);
    }

    /**
     * Whether a request with no session must be refused instead of given one. Called only for a session-less
     * {@code GET /api/csrf}, before the session is created; a caller that already has a session is never asked.
     */
    public synchronized boolean shedsNewSession() {
        Instant now = clock.instant();
        boolean before = episode.shedding();
        boolean shed;
        try {
            sample(now);
            shed = episode.decide(now, lastCount, lastFree);
        } catch (IOException | RuntimeException failure) {
            log.warn("Anonymous-session shed check could not be evaluated, so new anonymous sessions are shed: {}",
                    failure.getClass().getName());
            shed = episode.unevaluable(now);
        }
        if (shed && !before) {
            audit.emit(AuditEvent.SOURCE_THROTTLED,
                    AccountContext.sourceThrottled(SourceThrottleReason.DISK_RESERVE_SHED));
        } else if (!shed && before) {
            audit.emit(AuditEvent.SHED_EPISODE_CLEARED, AuditContext.NONE);
        }
        return shed;
    }

    /**
     * Whether an episode is running, as the last shed check decided. Runs no count and changes nothing, because anyone
     * can call it through {@code /actuator/health} (ADR-041).
     */
    public synchronized boolean shedding() {
        return episode.shedding();
    }

    /** Refreshes the count and the free space together, once per {@link #COUNT_TTL}; a failure keeps neither. */
    private void sample(Instant now) throws IOException {
        if (sampledAt == null || !now.isBefore(sampledAt.plus(COUNT_TTL))) {
            long count = rows.count();
            lastFree = volume.usableBytes();
            lastCount = count;
            sampledAt = now;
        }
    }
}
