package sg.securedhello.admin;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import sg.securedhello.admin.AuthenticableAdmins.Standing;
import sg.securedhello.audit.AdminRefusalReason;

/**
 * The admin mutation guard (ADR-048): a pure decision over the standings {@code AdminActions} locked, so it holds no
 * state and reads nothing itself. {@code AdminActions} is its only caller, from the one guarded method every admin
 * mutation goes through; an annotation or a filter would be opt-in, and the next endpoint would forget it.
 * <ol>
 *   <li><b>Check 1, actor &ne; subject:</b> an administrator may not act on their own account (REJ-050). It covers
 *       enable too: the standard bars self-directed updates through the admin endpoint, and a self-enable could only
 *       ever be a no-op, since a disabled admin cannot sign in. It covers unlock. It does not cover an admin-issued
 *       password reset, which an administrator may issue for their own account (ADR-006).</li>
 *   <li><b>Check 2, the two-admin invariant:</b> a mutation that can remove an admin (disable, demote and delete) is
 *       refused if the proposed state would leave fewer than two enrolled admins, counted with
 *       {@link Standing#enrolledAdmin()}. A mutation that lowers nothing, such as disabling a user, an unenrolled
 *       admin or an already disabled one, or any promotion, is never refused by it, whatever the count. Factor
 *       reset is exempt (ADR-049).</li>
 * </ol>
 */
final class AdminActionGuard {

    /** The fewest enrolled admins a guarded mutation may leave (ADR-048). */
    static final int MINIMUM_ENROLLED_ADMINS = 2;

    /**
     * An admin mutation, whether it can remove an admin from the invariant's count, and whether an admin may apply it
     * to their own account.
     */
    enum Mutation {
        ENABLE(false, false),
        DISABLE(true, false),
        PROMOTE(false, false),
        DEMOTE(true, false),
        DELETE(true, false),
        /**
         * Exempt from check 2 (ADR-049): the subject restores the count alone, by signing in and re-enrolling, so a
         * second admin able to act independently survives it.
         */
        FACTOR_RESET(false, false),
        UNLOCK(false, false),
        /** Admin-issued password reset: an administrator resetting their own password is legitimate (ADR-006). */
        PASSWORD_RESET(false, true);

        private final boolean removesAnAdmin;
        private final boolean selfActionAllowed;

        Mutation(boolean removesAnAdmin, boolean selfActionAllowed) {
            this.removesAnAdmin = removesAnAdmin;
            this.selfActionAllowed = selfActionAllowed;
        }
    }

    private AdminActionGuard() {
    }

    /**
     * Why {@code actor} may not apply {@code mutation} to {@code subject}, or empty if it may.
     *
     * @param lockSet the standings of every admin and of {@code subject}, read under the guard's lock set
     */
    static Optional<AdminRefusalReason> decide(Mutation mutation, UUID actor, UUID subject,
            Collection<Standing> lockSet) {
        if (!mutation.selfActionAllowed && actor.equals(subject)) {
            return Optional.of(AdminRefusalReason.SELF_ACTION);
        }
        if (mutation.removesAnAdmin && removesAnEnrolledAdmin(subject, lockSet)
                && enrolledAdmins(lockSet) - 1 < MINIMUM_ENROLLED_ADMINS) {
            return Optional.of(AdminRefusalReason.TWO_ADMIN_INVARIANT);
        }
        return Optional.empty();
    }

    private static boolean removesAnEnrolledAdmin(UUID subject, Collection<Standing> lockSet) {
        return lockSet.stream().anyMatch(standing -> standing.id().equals(subject) && standing.enrolledAdmin());
    }

    private static long enrolledAdmins(Collection<Standing> lockSet) {
        return lockSet.stream().filter(Standing::enrolledAdmin).count();
    }
}
