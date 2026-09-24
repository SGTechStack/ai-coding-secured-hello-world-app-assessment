package com.sgtechstack.helloworldauthapp.admin;

import com.sgtechstack.helloworldauthapp.passwordreset.PasswordResetTokenRepository;
import com.sgtechstack.helloworldauthapp.user.Role;
import com.sgtechstack.helloworldauthapp.user.User;
import com.sgtechstack.helloworldauthapp.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * No mutation may leave the system with zero administrators who can sign in.
 *
 * <h2>What the original finding got right, and the part worth being precise about</h2>
 *
 * The finding was that the only guard on admin mutations was "not yourself",
 * which protects a single admin from self-lockout and stops protecting anything
 * once there are two: each can disable, demote or delete the other, and every
 * step passes the self-check because every step targets somebody else.
 *
 * <p>Working through it carefully, the admin API on its own cannot quite reach
 * zero <em>sequentially</em>. Authorization requires the actor to be an enabled
 * admin, and the self-check requires the actor not to be the target — so whenever
 * a target is the last enabled admin, the actor would have to be a second enabled
 * admin, which contradicts the premise. Two admins genuinely can grind each other
 * down to one, as the finding says, and one is where the sequential path stops.
 *
 * <p>Three routes to zero remain, which is why the guard is not redundant:
 *
 * <ol>
 *   <li><strong>Self-service erasure.</strong> The sole admin deleting their own
 *       account reaches zero directly, and the self-check offers nothing because
 *       targeting yourself is the entire point of that endpoint. This is the one
 *       fully reachable path, and it is asserted in {@code AccountSelfServiceTest}.
 *       It is also new — added in the same change as this guard — so shipping the
 *       right without the guard would have <em>introduced</em> the hole the
 *       finding warned about.</li>
 *   <li><strong>Concurrency.</strong> Two admins removing each other at the same
 *       moment both read a count of two. The guard narrows the window to the
 *       gap between the count and the commit; closing it properly needs a
 *       constraint the database enforces. Recorded as residual rather than
 *       claimed as fixed.</li>
 *   <li><strong>Future mutation paths.</strong> A guard that exists and is named
 *       is one a new endpoint can be checked against.</li>
 * </ol>
 *
 * <h2>Why these tests call the service rather than the API</h2>
 *
 * Precisely because of the reasoning above: the HTTP layer cannot construct
 * "actor is authorized, target is the last enabled admin". Driving the service
 * directly is the only way to reach the branch, and the branch is worth covering
 * because the reachability argument depends on authorization and the self-check
 * continuing to hold — neither of which this class should have to assume.
 */
@SpringBootTest
@ActiveProfiles("dev")
class LastAdminProtectionTest {

    private static final String PASSWORD = "last-admin-password-1234";

    @Autowired
    private AdminUserManagementService managementService;

    @Autowired
    private LastAdminGuard lastAdminGuard;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User soleAdmin;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();

        soleAdmin = userRepository.save(new User("sole-admin", "sole-admin@example.com",
                passwordEncoder.encode(PASSWORD), Role.ADMIN, true));

        // Stands in for whoever is acting. Its role is irrelevant here:
        // authorization is enforced at the HTTP layer, and this class is about
        // the business invariant underneath it.
        actorId = userRepository.save(new User("guard-actor", "guard-actor@example.com",
                passwordEncoder.encode(PASSWORD), Role.USER, true)).getId();
    }

    @Test
    void disablingTheLastEnabledAdminIsRefused() {
        assertThatThrownBy(() -> managementService.setEnabled(actorId, soleAdmin.getId(), false))
                .isInstanceOf(LastAdminProtectedException.class)
                .hasMessageContaining("last enabled admin");

        assertThat(userRepository.findById(soleAdmin.getId()).orElseThrow().isEnabled())
                .as("the refusal has to leave the account untouched, not half-applied")
                .isTrue();
    }

    @Test
    void demotingTheLastEnabledAdminIsRefused() {
        assertThatThrownBy(() -> managementService.changeRole(actorId, soleAdmin.getId(), Role.USER))
                .isInstanceOf(LastAdminProtectedException.class);

        assertThat(userRepository.findById(soleAdmin.getId()).orElseThrow().getRole())
                .isEqualTo(Role.ADMIN);
    }

    @Test
    void deletingTheLastEnabledAdminIsRefused() {
        assertThatThrownBy(() -> managementService.deleteUser(actorId, soleAdmin.getId(), PASSWORD))
                .isInstanceOf(LastAdminProtectedException.class);

        assertThat(userRepository.findById(soleAdmin.getId())).isPresent();
    }

    @Test
    void theSameMutationsAreAllowedOnceASecondEnabledAdminExists() {
        // The control case, and the one that keeps the guard from being a blanket
        // "admins are immutable" rule. Without it every assertion above would
        // also pass if the guard rejected all admin mutations unconditionally.
        userRepository.save(new User("second-admin", "second-admin@example.com",
                passwordEncoder.encode(PASSWORD), Role.ADMIN, true));

        assertThatCode(() -> managementService.setEnabled(actorId, soleAdmin.getId(), false))
                .doesNotThrowAnyException();
    }

    @Test
    void aDisabledAdminDoesNotCountAsAnAdminWhoCouldTakeOver() {
        // The subtle half. A disabled ADMIN row cannot authenticate, so it cannot
        // re-enable itself or anyone else — counting it would permit removing the
        // last working admin on the strength of a dormant account. This is the
        // same mistake the bootstrap runner was making with existsByRole.
        userRepository.save(new User("dormant-admin", "dormant-admin@example.com",
                passwordEncoder.encode(PASSWORD), Role.ADMIN, false));

        assertThatThrownBy(() -> managementService.setEnabled(actorId, soleAdmin.getId(), false))
                .isInstanceOf(LastAdminProtectedException.class);
    }

    @Test
    void theGuardIgnoresTargetsThatHoldNoAdminCapability() {
        // A non-admin, and a disabled admin, can both be mutated freely: neither
        // is an administrator in any sense that matters, so neither can be the
        // last one. Asserted directly against the guard, since this is the
        // early-return branch that keeps it from touching the database.
        User regular = userRepository.save(new User("guard-regular", "guard-regular@example.com",
                passwordEncoder.encode(PASSWORD), Role.USER, true));
        User dormantAdmin = userRepository.save(new User("guard-dormant", "guard-dormant@example.com",
                passwordEncoder.encode(PASSWORD), Role.ADMIN, false));

        assertThatCode(() -> lastAdminGuard.requireNotLastEnabledAdmin(regular, "delete"))
                .doesNotThrowAnyException();
        assertThatCode(() -> lastAdminGuard.requireNotLastEnabledAdmin(dormantAdmin, "delete"))
                .doesNotThrowAnyException();
    }
}
