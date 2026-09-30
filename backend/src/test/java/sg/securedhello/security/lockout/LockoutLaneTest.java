package sg.securedhello.security.lockout;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import sg.securedhello.security.source.DeviceClaim;
import sg.securedhello.security.source.SourceKey;
import sg.securedhello.security.source.SourceKeyAuthenticationDetails;
import sg.securedhello.testsupport.Proves;

/** Which lane a sign-in counts in, and which lock refuses it (ADR-075), on fixed instants. */
class LockoutLaneTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final UUID DEVICE = UUID.randomUUID();

    private static SourceKeyAuthenticationDetails details(UUID owner, Instant expiresAt, Instant lockedUntil) {
        return new SourceKeyAuthenticationDetails(SourceKey.UNPARSEABLE,
                new DeviceClaim(DEVICE, owner, expiresAt, lockedUntil));
    }

    @Test
    @Proves("T-LCK-024")
    void aValidClaimForTheAccountIsTrustedAndAnUntrustedLockNeverRefusesIt() {
        SourceKeyAuthenticationDetails trusted = details(ACCOUNT, NOW.plusSeconds(1), null);
        assertThat(LockoutLane.of(trusted, ACCOUNT, NOW)).isEqualTo(new LockoutLane(DEVICE));
        assertThat(LockoutLane.of(trusted, ACCOUNT, NOW).trusted()).isTrue();
        assertThat(LockoutLane.locked(trusted, ACCOUNT, true, NOW)).isFalse();
    }

    @Test
    @Proves("T-LCK-025")
    void aTrustedDeviceIsRefusedByItsOwnLockOnly() {
        assertThat(LockoutLane.locked(details(ACCOUNT, NOW.plusSeconds(1), NOW.plusSeconds(1)), ACCOUNT, false, NOW))
                .isTrue();
        assertThat(LockoutLane.locked(details(ACCOUNT, NOW.plusSeconds(1), NOW), ACCOUNT, false, NOW))
                .as("a lock ending now has lifted").isFalse();
    }

    @Test
    @Proves("T-LCK-026")
    void aClaimForAnotherAccountIsUntrustedAndTheUntrustedLockApplies() {
        SourceKeyAuthenticationDetails others = details(UUID.randomUUID(), NOW.plusSeconds(1), null);
        assertThat(LockoutLane.of(others, ACCOUNT, NOW)).isEqualTo(LockoutLane.UNTRUSTED);
        assertThat(LockoutLane.locked(others, ACCOUNT, true, NOW)).isTrue();
        assertThat(LockoutLane.locked(others, ACCOUNT, false, NOW)).isFalse();
    }

    @Test
    @Proves("T-LCK-027")
    void anExpiredClaimNoClaimAndForeignDetailsAreUntrusted() {
        assertThat(LockoutLane.of(details(ACCOUNT, NOW, null), ACCOUNT, NOW)).as("expiring now")
                .isEqualTo(LockoutLane.UNTRUSTED);
        assertThat(LockoutLane.locked(details(ACCOUNT, NOW, NOW.plusSeconds(1)), ACCOUNT, false, NOW))
                .as("an expired device's own lock no longer applies; the untrusted lane's does").isFalse();
        assertThat(LockoutLane.of(new SourceKeyAuthenticationDetails(SourceKey.UNPARSEABLE), ACCOUNT, NOW).trusted())
                .isFalse();
        assertThat(LockoutLane.of("foreign", ACCOUNT, NOW)).isEqualTo(LockoutLane.UNTRUSTED);
        assertThat(LockoutLane.locked(null, ACCOUNT, true, NOW)).isTrue();
    }
}
