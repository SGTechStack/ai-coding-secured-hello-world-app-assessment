package sg.securedhello.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import sg.securedhello.admin.AdminActionGuard.Mutation;
import sg.securedhello.admin.AuthenticableAdmins.Standing;
import sg.securedhello.audit.AdminRefusalReason;

/** The guard's pure decision (ADR-048): check 1, actor &ne; subject, then the two-admin invariant. */
class AdminActionGuardTest {

    private static final UUID ACTOR = UUID.randomUUID();

    private static Standing enrolledAdmin(UUID id) {
        return new Standing(id, true, true, true, false, true, false);
    }

    private static Standing user(UUID id) {
        return new Standing(id, false, true, true, false, false, false);
    }

    /** The actor plus {@code others} further enrolled admins, whose ids are added to {@code ids}. */
    private static List<Standing> enrolledAdmins(int others, List<UUID> ids) {
        List<Standing> standings = new ArrayList<>(List.of(enrolledAdmin(ACTOR)));
        for (int i = 0; i < others; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            standings.add(enrolledAdmin(id));
        }
        return standings;
    }

    @ParameterizedTest
    @EnumSource(value = Mutation.class, mode = EnumSource.Mode.EXCLUDE, names = "PASSWORD_RESET")
    void anAdminActingOnTheirOwnAccountIsRefusedWhateverTheCount(Mutation mutation) {
        List<Standing> lockSet = enrolledAdmins(5, new ArrayList<>());

        assertThat(AdminActionGuard.decide(mutation, ACTOR, ACTOR, lockSet))
                .contains(AdminRefusalReason.SELF_ACTION);
    }

    /** ADR-006: an administrator resetting their own password is legitimate, so no self-action rule applies to it. */
    @Test
    void anAdminIssuingAResetForTheirOwnAccountIsAllowedWhateverTheCount() {
        assertThat(AdminActionGuard.decide(Mutation.PASSWORD_RESET, ACTOR, ACTOR, enrolledAdmins(1, new ArrayList<>())))
                .isEmpty();
        assertThat(AdminActionGuard.decide(Mutation.PASSWORD_RESET, ACTOR, ACTOR, enrolledAdmins(5, new ArrayList<>())))
                .isEmpty();
    }

    @Test
    void neitherUnlockNorResetCanRemoveAnAdminSoTheInvariantNeverRefusesThem() {
        List<UUID> others = new ArrayList<>();
        List<Standing> lockSet = enrolledAdmins(1, others);

        assertThat(AdminActionGuard.decide(Mutation.UNLOCK, ACTOR, others.get(0), lockSet)).isEmpty();
        assertThat(AdminActionGuard.decide(Mutation.PASSWORD_RESET, ACTOR, others.get(0), lockSet)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = Mutation.class, names = {"DISABLE", "DEMOTE", "DELETE"})
    void atExactlyTwoEnrolledAdminsRemovingTheOtherIsRefused(Mutation mutation) {
        List<UUID> others = new ArrayList<>();
        List<Standing> lockSet = enrolledAdmins(1, others);

        assertThat(AdminActionGuard.decide(mutation, ACTOR, others.get(0), lockSet))
                .contains(AdminRefusalReason.TWO_ADMIN_INVARIANT);
    }

    @ParameterizedTest
    @EnumSource(value = Mutation.class, names = {"DISABLE", "DEMOTE", "DELETE"})
    void atThreeEnrolledAdminsRemovingOneIsAllowed(Mutation mutation) {
        List<UUID> others = new ArrayList<>();
        List<Standing> lockSet = enrolledAdmins(2, others);

        assertThat(AdminActionGuard.decide(mutation, ACTOR, others.get(0), lockSet)).isEmpty();
    }

    /** ADR-049: at exactly two enrolled admins a factor reset of the other passes; only check 1 applies to it. */
    @Test
    void atExactlyTwoEnrolledAdminsResettingTheOthersFactorIsAllowed() {
        List<UUID> others = new ArrayList<>();
        List<Standing> lockSet = enrolledAdmins(1, others);

        assertThat(AdminActionGuard.decide(Mutation.FACTOR_RESET, ACTOR, others.get(0), lockSet)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = Mutation.class, names = {"ENABLE", "PROMOTE"})
    void enablingOrPromotingIsNeverRefusedByTheCount(Mutation mutation) {
        UUID subject = UUID.randomUUID();
        List<Standing> lockSet = List.of(enrolledAdmin(ACTOR), enrolledAdmin(subject));

        assertThat(AdminActionGuard.decide(mutation, ACTOR, subject, lockSet)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = Mutation.class, names = {"DISABLE", "DEMOTE", "DELETE"})
    void removingSomeoneWhoIsNotCountedLowersNothingSoIsAllowedAtAnyCount(Mutation mutation) {
        UUID user = UUID.randomUUID();
        UUID invite = UUID.randomUUID();
        UUID disabled = UUID.randomUUID();
        UUID unenrolled = UUID.randomUUID();
        List<Standing> lockSet = List.of(enrolledAdmin(ACTOR), user(user),
                new Standing(invite, true, true, false, false, true, false),
                new Standing(disabled, true, false, true, false, true, false),
                new Standing(unenrolled, true, true, true, false, false, false));

        for (UUID subject : List.of(user, invite, disabled, unenrolled)) {
            assertThat(AdminActionGuard.decide(mutation, ACTOR, subject, lockSet)).as("%s", subject).isEmpty();
        }
    }

    @Test
    void aPendingInviteDoesNotCountSoOneRealAdminPlusAnInviteReadsAsOne() {
        UUID other = UUID.randomUUID();
        UUID invite = UUID.randomUUID();
        // Activated_at unset: an invite, even one holding a factor row, is not an enrolled admin (ADR-006; T-ADM-010).
        List<Standing> lockSet = List.of(enrolledAdmin(ACTOR), enrolledAdmin(other),
                new Standing(invite, true, true, false, false, true, false));

        assertThat(AdminActionGuard.decide(Mutation.DISABLE, ACTOR, other, lockSet))
                .contains(AdminRefusalReason.TWO_ADMIN_INVARIANT);
    }

    @Test
    void theInvariantCountsTheEnrolmentTermsOnlySoACappedOrTierTwoAdminStillCounts() {
        UUID subject = UUID.randomUUID();
        UUID capped = UUID.randomUUID();
        UUID tierTwo = UUID.randomUUID();
        // Three enrolled admins, two of them not authenticable: the guard counts three, so a disable leaves two.
        List<Standing> lockSet = List.of(enrolledAdmin(ACTOR), enrolledAdmin(subject),
                new Standing(capped, true, true, true, true, true, false));
        List<Standing> tierTwoSet = List.of(enrolledAdmin(ACTOR), enrolledAdmin(subject),
                new Standing(tierTwo, true, true, true, false, true, true));

        assertThat(AdminActionGuard.decide(Mutation.DISABLE, ACTOR, subject, lockSet)).isEmpty();
        assertThat(AdminActionGuard.decide(Mutation.DISABLE, ACTOR, subject, tierTwoSet)).isEmpty();
    }

    @Test
    void theStandingPredicatesSplitTheEnrolmentTermsFromAuthenticable() {
        UUID id = UUID.randomUUID();
        assertThat(enrolledAdmin(id).authenticable()).isTrue();
        assertThat(new Standing(id, true, true, true, true, true, false).enrolledAdmin()).isTrue();
        assertThat(new Standing(id, true, true, true, true, true, false).authenticable()).isFalse();
        assertThat(new Standing(id, true, true, true, false, true, true).authenticable()).isFalse();
        assertThat(user(id).enrolledAdmin()).isFalse();
        assertThat(new Standing(id, true, false, true, false, true, false).enrolledAdmin()).isFalse();
        assertThat(new Standing(id, true, true, false, false, true, false).enrolledAdmin()).isFalse();
        assertThat(new Standing(id, true, true, true, false, false, false).enrolledAdmin()).isFalse();
    }
}
