package com.sgtechstack.helloworldauthapp.admin;

import com.sgtechstack.helloworldauthapp.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The single sanctioned checkpoint for mutating a user's {@code role}
 * column. {@link AdminUserManagementService#changeRole} is the only
 * production caller of {@code User.setRole(...)} — enforced by {@code
 * RoleMutationInvariantTest}, which fails the build if a second caller is
 * ever introduced — and it must route through this guard rather than
 * calling {@code setRole} directly.
 *
 * This exists in place of the Spring-Data-REST {@code
 * ImmutableSecurityHandler} pattern described in
 * {@code App-Standards/Appfw-User-Standards/Shared_Recipes/Common_Role-Based_Access_Control_Configuration.md}:
 * that pattern blocks role mutation at a {@code @RepositoryEventHandler}
 * hook, which only exists if a repository is exposed over HTTP by Spring
 * Data REST. {@code UserRepository} is never exposed that way here (no
 * {@code spring-boot-starter-data-rest} dependency, no {@code
 * @RepositoryRestResource}), so there is no such hook to attach to.
 * Instead, this class plays the same role the handler would: a single,
 * named, independently testable place that both documents and audits
 * "this is the only sanctioned way to change a role", so the invariant is
 * explicit rather than implicit in "no other endpoint happens to exist
 * today".
 *
 * Authorization (that the caller is an {@code ADMIN}) and the self-action
 * business rule are both enforced before this point ({@code
 * SecurityConfig}'s URL guard matrix and {@code
 * AdminUserManagementService#requireNotSelf} respectively); this class
 * does not re-check either. Its only job is the audit record and the
 * structural invariant.
 */
@Component
public class RoleMutationGuard {

    private static final Logger log = LoggerFactory.getLogger(RoleMutationGuard.class);

    /**
     * Records that a role mutation is about to happen through the
     * sanctioned path. Called immediately before {@code User.setRole(...)}
     * in {@link AdminUserManagementService#changeRole}.
     */
    public void recordSanctionedMutation(UUID actingAdminId, UUID targetUserId, Role previousRole, Role newRole) {
        log.info(
                "Role mutation via sanctioned path actorId={} targetUserId={} previousRole={} newRole={}",
                actingAdminId, targetUserId, previousRole, newRole
        );
    }
}
