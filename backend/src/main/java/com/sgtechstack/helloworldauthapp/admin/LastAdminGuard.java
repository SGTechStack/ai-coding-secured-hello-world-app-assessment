package com.sgtechstack.helloworldauthapp.admin;

import com.sgtechstack.helloworldauthapp.user.Role;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import org.springframework.stereotype.Component;

/**
 * Refuses any mutation that would leave zero administrators able to sign in.
 *
 * <h2>The gap this closes</h2>
 *
 * The only protection on admin mutations used to be "not yourself". That reads
 * like it prevents self-lockout, and for a single admin it does. With two it
 * does not: each can disable, demote or delete the other, so two legitimate
 * admins disagreeing — or one compromised session — can walk the system down to
 * one administrator and then to none, with every individual step passing the
 * self-action check because each targeted somebody else.
 *
 * <p>From zero, there is no way back through the application. Login requires an
 * account; promoting an account requires an admin; and the bootstrap runner only
 * seeds when no enabled admin exists — which, after this scenario, is true, but
 * only helps on a restart. That is the state the admin-bootstrap story exists to
 * make unnecessary, so leaving the UI able to produce it was a real gap rather
 * than a theoretical one.
 *
 * <h2>Enabled, not merely present</h2>
 *
 * The count is of admins who are <em>enabled</em>. A disabled admin row is not
 * an administrator in any sense that matters: it cannot authenticate, so it
 * cannot re-enable itself or anyone else. Counting rows rather than usable
 * accounts would let the last working admin be disabled as long as a dormant
 * one existed somewhere — which is the same class of mistake the bootstrap
 * runner was making with {@code existsByRole}.
 *
 * <h2>Why a separate component</h2>
 *
 * Three call sites in {@link AdminUserManagementService} plus the self-service
 * erasure path need this rule, and it has to be the same rule in all four —
 * an invariant enforced in three places out of four is not an invariant. Naming
 * it also makes it directly testable, and makes a future fifth mutation path an
 * obvious place to ask "does this need the guard?".
 */
@Component
public class LastAdminGuard {

    private final UserRepository userRepository;

    public LastAdminGuard(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Rejects {@code action} when {@code target} is currently the only admin
     * who can sign in.
     *
     * <p>Callers pass the target whose admin capability is about to be removed
     * — by disabling it, demoting it, or deleting it. A target that is not an
     * enabled admin cannot be the last one, so those cases return without
     * touching the database.
     *
     * @throws LastAdminProtectedException if the mutation would leave no
     *                                     enabled administrator
     */
    public void requireNotLastEnabledAdmin(User target, String action) {
        if (target.getRole() != Role.ADMIN || !target.isEnabled()) {
            return;
        }

        if (userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new LastAdminProtectedException(
                    "Cannot " + action + " the last enabled admin account — promote another admin first");
        }
    }
}
