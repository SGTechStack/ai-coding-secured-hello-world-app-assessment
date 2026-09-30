package sg.securedhello.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import sg.securedhello.audit.RecoveryRunContext;
import sg.securedhello.testsupport.Proves;

/** The plan/apply digest binds every field of the account state it previews (ADR-074). */
class RecoveryPlanTest {

    private static final RecoveryRunContext.Database DATABASE =
            new RecoveryRunContext.Database("/srv/data/secured-hello", "8", "2026-01-01T00:00:00Z");
    private static final Instant T = Instant.parse("2026-01-01T00:00:00Z");
    private static final RecoveryPlan.Account ACCOUNT = new RecoveryPlan.Account(
            UUID.fromString("00000000-0000-4000-8000-000000000001"), "alice", true, null, T, T);

    private static String single(RecoveryScope scope, RecoveryPlan.Account account,
            RecoveryRunContext.Database database) {
        return new RecoveryPlan(scope, List.of(account), null, database).digest();
    }

    @Test
    @Proves("T-RUN-011")
    void everyTupleFieldMovesTheDigest() {
        String digest = single(RecoveryScope.PASSWORD, ACCOUNT, DATABASE);
        assertThat(digest).matches("[0-9a-f]{64}")
                .isEqualTo(single(RecoveryScope.PASSWORD, ACCOUNT, DATABASE));

        List<UnaryOperator<RecoveryPlan.Account>> changes = List.of(
                a -> new RecoveryPlan.Account(UUID.randomUUID(), a.username(), a.credentialSet(),
                        a.passwordDisabledAt(), a.credentialIssuedAt(), a.factorCreatedAt()),
                a -> new RecoveryPlan.Account(a.id(), a.username(), false, a.passwordDisabledAt(),
                        a.credentialIssuedAt(), a.factorCreatedAt()),
                a -> new RecoveryPlan.Account(a.id(), a.username(), a.credentialSet(), T, a.credentialIssuedAt(),
                        a.factorCreatedAt()),
                a -> new RecoveryPlan.Account(a.id(), a.username(), a.credentialSet(), a.passwordDisabledAt(),
                        T.plusNanos(1000), a.factorCreatedAt()),
                a -> new RecoveryPlan.Account(a.id(), a.username(), a.credentialSet(), a.passwordDisabledAt(),
                        a.credentialIssuedAt(), null),
                a -> new RecoveryPlan.Account(a.id(), a.username(), a.credentialSet(), a.passwordDisabledAt(),
                        a.credentialIssuedAt(), T.plusSeconds(1)));
        assertThat(changes).allSatisfy(change ->
                assertThat(single(RecoveryScope.PASSWORD, change.apply(ACCOUNT), DATABASE)).isNotEqualTo(digest));

        assertThat(Stream.of(RecoveryScope.TOTP, RecoveryScope.BOTH).map(scope -> single(scope, ACCOUNT, DATABASE)))
                .doesNotContain(digest).doesNotHaveDuplicates();
        assertThat(single(RecoveryScope.PASSWORD, ACCOUNT,
                new RecoveryRunContext.Database("/srv/copy/secured-hello", "8", DATABASE.modifiedAt())))
                .isNotEqualTo(digest);
        assertThat(single(RecoveryScope.PASSWORD, ACCOUNT,
                new RecoveryRunContext.Database(DATABASE.path(), "9", DATABASE.modifiedAt()))).isNotEqualTo(digest);
    }

    @Test
    void theFileTimeAndTheUsernameAreNotBound() {
        // The file time moves on every write, and is printed for the operator to compare instead (R-RUN-008).
        String digest = single(RecoveryScope.PASSWORD, ACCOUNT, DATABASE);
        assertThat(single(RecoveryScope.PASSWORD, ACCOUNT,
                new RecoveryRunContext.Database(DATABASE.path(), "8", "2027-01-01T00:00:00Z"))).isEqualTo(digest);
    }

    @Test
    @Proves("T-RUN-011")
    void aBatchDigestBindsEachAccountAndTheInputFile() {
        RecoveryPlan.Account other = new RecoveryPlan.Account(UUID.randomUUID(), "bob", true, null, null, null);
        String batch = new RecoveryPlan(RecoveryScope.PASSWORD, List.of(ACCOUNT, other), "f".repeat(64), DATABASE)
                .digest();

        RecoveryPlan.Account moved = new RecoveryPlan.Account(other.id(), "bob", true, T, null, null);
        assertThat(new RecoveryPlan(RecoveryScope.PASSWORD, List.of(ACCOUNT, moved), "f".repeat(64), DATABASE)
                .digest()).isNotEqualTo(batch);
        assertThat(new RecoveryPlan(RecoveryScope.PASSWORD, List.of(ACCOUNT, other), "e".repeat(64), DATABASE)
                .digest()).isNotEqualTo(batch);
        assertThat(new RecoveryPlan(RecoveryScope.PASSWORD, List.of(ACCOUNT, other), null, DATABASE).digest())
                .isNotEqualTo(batch);
    }

    @Test
    void anAccountWithoutAFactorRowHasNoFactorAndThePlanNamesItsTargets() {
        RecoveryPlan.Account noFactor = new RecoveryPlan.Account(UUID.randomUUID(), "bob", true, null, null, null);
        assertThat(noFactor.hasFactor()).isFalse();
        assertThat(ACCOUNT.hasFactor()).isTrue();
        assertThat(new RecoveryPlan(RecoveryScope.TOTP, List.of(ACCOUNT, noFactor), null, DATABASE).targets())
                .containsExactly(ACCOUNT.id(), noFactor.id());
    }

    @Test
    void theHelperIsSha256() {
        assertThat(RecoveryPlan.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void theDatasourceUrlOnlyOpensAnExistingDatabase() {
        assertThat(RunnerMode.ifExists("jdbc:h2:file:./data/x;LOCK_TIMEOUT=1000"))
                .isEqualTo("jdbc:h2:file:./data/x;LOCK_TIMEOUT=1000;IFEXISTS=TRUE");
        assertThat(RunnerMode.ifExists("jdbc:h2:file:./data/x;ifexists=false;LOCK_TIMEOUT=1000"))
                .isEqualTo("jdbc:h2:file:./data/x;LOCK_TIMEOUT=1000;IFEXISTS=TRUE");
    }
}
