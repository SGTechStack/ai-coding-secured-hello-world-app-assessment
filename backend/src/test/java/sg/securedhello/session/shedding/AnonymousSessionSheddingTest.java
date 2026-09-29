package sg.securedhello.session.shedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.dao.DataAccessResourceFailureException;

import sg.securedhello.audit.AccountContext;
import sg.securedhello.audit.AuditContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.audit.SourceThrottleReason;
import sg.securedhello.testsupport.MutableClock;
import sg.securedhello.testsupport.Proves;

/** ADR-041: the one cached, single-flight count, the fail-shed rule, the episode rows and the health reader. */
class AnonymousSessionSheddingTest {

    private static final long FLOOR = 1_000;

    private final MutableClock clock = MutableClock.startingNow();
    private final AuditEmitter audit = mock(AuditEmitter.class);
    private final AtomicInteger counts = new AtomicInteger();
    private final AtomicLong rows = new AtomicLong();
    private final AtomicLong free = new AtomicLong(Long.MAX_VALUE / 2);

    private final AnonymousSessionShedding shedding = new AnonymousSessionShedding(() -> {
        counts.incrementAndGet();
        return rows.get();
    }, free::get, clock, audit, FLOOR);

    @Test
    @Proves("T-OBS-006")
    void oneCountAnswersForOneSecond() {
        shedding.shedsNewSession();
        clock.advance(AnonymousSessionShedding.COUNT_TTL.minusMillis(1));
        shedding.shedsNewSession();
        assertThat(counts).hasValue(1);

        clock.advance(Duration.ofMillis(1));
        shedding.shedsNewSession();
        assertThat(counts).hasValue(2);
    }

    @Test
    @Proves("T-OBS-006")
    void parallelChecksAtAnExpiryCauseExactlyOneCount() throws Exception {
        CountDownLatch counting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger slowCounts = new AtomicInteger();
        AnonymousSessionShedding slow = new AnonymousSessionShedding(() -> {
            slowCounts.incrementAndGet();
            counting.countDown();
            await(release);
            return 0;
        }, free::get, clock, audit, FLOOR);

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Thread thread = Thread.ofPlatform().start(slow::shedsNewSession);
            threads.add(thread);
        }
        counting.await();
        // Every other caller is now queued behind the one that is counting.
        while (threads.stream().filter(t -> t.getState() == Thread.State.BLOCKED).count() < threads.size() - 1) {
            Thread.onSpinWait();
        }
        release.countDown();
        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(slowCounts).hasValue(1);
    }

    @Test
    @Proves("T-OBS-006")
    void theHealthIndicatorNeverCounts() {
        HealthIndicator h2Data = new SheddingConfig().h2DataHealthIndicator(shedding);

        for (int i = 0; i < 5; i++) {
            clock.advance(Duration.ofSeconds(2));
            assertThat(h2Data.health().getStatus()).isEqualTo(Status.UP);
        }

        assertThat(counts).hasValue(0);
    }

    @Test
    void theHealthIndicatorReportsTheEpisodeDetailFree() {
        HealthIndicator h2Data = new SheddingConfig().h2DataHealthIndicator(shedding);
        rows.set(ShedEpisode.N_MAX);

        shedding.shedsNewSession();

        assertThat(h2Data.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(h2Data.health().getDetails()).isEmpty();
    }

    @Test
    @Proves("T-RL-024")
    void aFailedCountSheds() {
        AnonymousSessionShedding failing = new AnonymousSessionShedding(() -> {
            throw new DataAccessResourceFailureException("closed");
        }, free::get, clock, audit, FLOOR);

        assertThat(failing.shedsNewSession()).isTrue();
        assertThat(failing.shedding()).isTrue();
    }

    @Test
    @Proves("T-RL-024")
    void aFailedFreeSpaceReadSheds() {
        AnonymousSessionShedding failing = new AnonymousSessionShedding(() -> 0, () -> {
            throw new IOException("gone");
        }, clock, audit, FLOOR);

        assertThat(failing.shedsNewSession()).isTrue();
    }

    @Test
    @Proves("T-RL-024")
    void theFreeSpaceLineUsesTheConfiguredFloor() {
        free.set(FLOOR - 1);
        assertThat(shedding.shedsNewSession()).isTrue();
    }

    @Test
    void theFreeSpaceIsSampledWithTheCount() {
        shedding.shedsNewSession();
        free.set(0);
        assertThat(shedding.shedsNewSession()).as("inside the sample's second").isFalse();

        clock.advance(AnonymousSessionShedding.COUNT_TTL);
        assertThat(shedding.shedsNewSession()).as("the next sample sees the low disk").isTrue();
    }

    @Test
    void anEpisodeWritesOneStartRowAndOneClearRow() {
        rows.set(ShedEpisode.N_MAX);
        for (int i = 0; i < 5; i++) {
            shedding.shedsNewSession();
            clock.advance(Duration.ofSeconds(1));
        }
        rows.set(0);
        clock.advance(ShedEpisode.DWELL);
        for (int i = 0; i < 5; i++) {
            shedding.shedsNewSession();
            clock.advance(Duration.ofSeconds(1));
        }

        verify(audit).emit(AuditEvent.SOURCE_THROTTLED,
                AccountContext.sourceThrottled(SourceThrottleReason.DISK_RESERVE_SHED));
        verify(audit).emit(AuditEvent.SHED_EPISODE_CLEARED, AuditContext.NONE);
        verifyNoMoreInteractions(audit);
    }

    @Test
    void noEpisodeWritesNoRow() {
        shedding.shedsNewSession();

        verify(audit, never()).emit(any(), any());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
