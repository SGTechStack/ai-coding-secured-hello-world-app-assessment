package sg.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static sg.securedhello.audit.AuditRowDefinition.row;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import sg.securedhello.audit.AuditRowDefinition.Keying;
import sg.securedhello.audit.AuditRowDefinition.Scope;
import sg.securedhello.testsupport.MutableClock;
import sg.securedhello.testsupport.Proves;

/**
 * The emitter-side bound (ADR-019) as a pure structure: keyed entries with their count and first-seen time, the
 * distinct-key cap with no eviction, and the truncation row's two exact numbers (REJ-078; REJ-079).
 */
class AuditKeyingTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final AuditRowDefinition SOURCE_ROW = row("test-source-row", "Source row.")
            .reasons(CsrfReason.class).keyed(Keying.SOURCE).build();
    private static final AuditRowDefinition OTHER_SOURCE_ROW = row("test-other-row", "Other row.")
            .keyed(Keying.SOURCE).build();
    private static final AuditRowDefinition USER_ROW = row("test-user-row", "User row.")
            .required(AuditKey.USER_ID).keyed(Keying.USER).build();

    private final MutableClock clock = MutableClock.startingAt(Instant.parse("2026-03-01T09:00:00Z"));
    private final List<Map<String, Object>> keyed = new ArrayList<>();
    private final List<TruncationContext> truncated = new ArrayList<>();
    private final AuditKeying keying = new AuditKeying(clock, WINDOW, 3, 2, new AuditKeying.Sink() {
        @Override
        public void keyed(AuditRowDefinition row, Map<String, Object> fields) {
            keyed.add(fields);
        }

        @Override
        public void truncated(TruncationContext context) {
            truncated.add(context);
        }
    });

    private void source(String source, AuditRowDefinition row, String reason) {
        keying.record(row == SOURCE_ROW ? "SOURCE_ROW" : "OTHER_ROW", row,
                reason == null ? Map.of("source.ip_hash", source) : Map.of("source.ip_hash", source,
                        "event.reason", reason));
    }

    @Test
    void occurrencesOfOneKeyRowAndReasonCollapseIntoOneRowWithTheirCountAndFirstSeenTime() {
        Instant first = clock.instant();
        source("s1", SOURCE_ROW, "CSRF_MISSING");
        clock.advance(Duration.ofMinutes(1));
        source("s1", SOURCE_ROW, "CSRF_MISSING");
        source("s1", SOURCE_ROW, "CSRF_INVALID");
        source("s1", OTHER_SOURCE_ROW, null);
        assertThat(keyed).as("nothing is written before the window closes").isEmpty();

        keying.close();

        assertThat(keyed).containsExactly(
                Map.of("source.ip_hash", "s1", "event.reason", "CSRF_MISSING", "event.count", 2L,
                        "event.start", first.toString()),
                Map.of("source.ip_hash", "s1", "event.reason", "CSRF_INVALID", "event.count", 1L,
                        "event.start", clock.instant().toString()),
                Map.of("source.ip_hash", "s1", "event.count", 1L, "event.start", clock.instant().toString()));
        assertThat(truncated).isEmpty();
    }

    @Test
    @Proves("T-AUD-036")
    void theTruncationRowCarriesTheTrackedCountAndTheExactUntrackedCount() {
        for (String source : List.of("s1", "s2", "s3", "s4", "s4", "s5", "s1", "s5", "s5")) {
            source(source, SOURCE_ROW, "CSRF_MISSING");
        }
        source("s6", OTHER_SOURCE_ROW, null);

        keying.close();

        assertThat(keyed).extracting(fields -> fields.get("source.ip_hash")).containsExactly("s1", "s2", "s3");
        assertThat(keyed).extracting(fields -> fields.get("event.count")).containsExactly(2L, 1L, 1L);
        assertThat(truncated).containsExactly(new TruncationContext(TruncationReason.SOURCE_CAP_REACHED, 3, 6,
                List.of("OTHER_ROW", "SOURCE_ROW")));
    }

    @Test
    void aKeyBeyondTheCapIsNeverAdmittedLaterInTheWindow() {
        source("s1", SOURCE_ROW, "CSRF_MISSING");
        source("s2", SOURCE_ROW, "CSRF_MISSING");
        source("s3", SOURCE_ROW, "CSRF_MISSING");
        source("s4", SOURCE_ROW, "CSRF_MISSING");
        source("s1", OTHER_SOURCE_ROW, null);
        source("s4", OTHER_SOURCE_ROW, null);

        keying.close();

        assertThat(keyed).extracting(fields -> fields.get("source.ip_hash")).containsExactly("s1", "s2", "s3", "s1");
        assertThat(truncated).singleElement().extracting(TruncationContext::untrackedCount).isEqualTo(2L);
    }

    @Test
    void theUserTierHasItsOwnCap() {
        for (int i = 0; i < 3; i++) {
            UUID user = UUID.randomUUID();
            keying.record("USER_ROW", USER_ROW, Map.of("user.id", user.toString(), "source.ip_hash", "s1"));
        }
        source("s1", SOURCE_ROW, "CSRF_MISSING");

        keying.close();

        assertThat(keyed).hasSize(3);
        assertThat(truncated).containsExactly(new TruncationContext(TruncationReason.USER_CAP_REACHED, 2, 1,
                List.of("USER_ROW")));
    }

    @Test
    void theWindowOpensAtTheFirstOccurrenceAndClosesWhenItHasRunItsLength() {
        source("s1", SOURCE_ROW, "CSRF_MISSING");
        clock.advance(WINDOW.minusMillis(1));
        keying.closeIfDue();
        source("s1", SOURCE_ROW, "CSRF_MISSING");
        assertThat(keyed).isEmpty();

        clock.advance(Duration.ofMillis(1));
        keying.closeIfDue();
        assertThat(keyed).singleElement().extracting(fields -> fields.get("event.count")).isEqualTo(2L);

        clock.advance(Duration.ofHours(1));
        keying.closeIfDue();
        source("s1", SOURCE_ROW, "CSRF_MISSING");
        clock.advance(WINDOW.minusMillis(1));
        source("s2", SOURCE_ROW, "CSRF_MISSING");
        assertThat(keyed).as("an idle gap opens no window").hasSize(1);
        clock.advance(Duration.ofMillis(1));
        source("s3", SOURCE_ROW, "CSRF_MISSING");
        assertThat(keyed).as("the occurrence that finds the window over closes it first").hasSize(3);
    }

    @Test
    void closingAWindowResetsTheCaps() {
        for (String source : List.of("s1", "s2", "s3", "s4")) {
            source(source, SOURCE_ROW, "CSRF_MISSING");
        }
        keying.close();
        keyed.clear();
        truncated.clear();

        source("s4", SOURCE_ROW, "CSRF_MISSING");
        keying.close();

        assertThat(keyed).singleElement().extracting(fields -> fields.get("source.ip_hash")).isEqualTo("s4");
        assertThat(truncated).isEmpty();
    }

    @Test
    void aRowWithoutItsKeyIsWrittenAtOnce() {
        keying.record("USER_ROW", USER_ROW, Map.of("source.ip_hash", "s1"));

        assertThat(keyed).containsExactly(Map.of("source.ip_hash", "s1"));
    }

    @Test
    void aKeyedRowMustBeRequestScoped() {
        assertThatIllegalArgumentException().isThrownBy(() -> row("a", "m").scope(Scope.PROCESS)
                .keyed(Keying.SOURCE).build());
    }
}
