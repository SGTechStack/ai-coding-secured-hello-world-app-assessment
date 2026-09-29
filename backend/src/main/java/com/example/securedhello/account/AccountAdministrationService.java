package com.example.securedhello.account;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account administration for Admins. Every method checks the Admin role itself with
 * {@code @PreAuthorize}, in addition to the {@code /api/admin/**} URL rule, so no caller reaches it
 * as a User.
 */
@Service
class AccountAdministrationService {

	private final AccountRepository accounts;

	private final Clock clock;

	AccountAdministrationService(AccountRepository accounts, Clock clock) {
		this.accounts = accounts;
		this.clock = clock;
	}

	/** Every Account, oldest first, with its role and enabled and Locked state. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	List<AdminAccountView> list() {
		Instant now = clock.instant();
		return accounts.findAll(Sort.by("createdAt", "username"))
			.stream()
			.map((account) -> AdminAccountView.of(account, now))
			.toList();
	}

	/**
	 * Enables or disables an Account. Never touches the required-password-change flag: a re-enable is
	 * not treated as a suspected compromise (ADR 0001). Ending the target's Sessions and auditing the
	 * change are the caller's job, once this has committed.
	 * @throws AccountNotFoundException when {@code targetId} is not an Account
	 * @throws SelfActionForbiddenException when the acting Admin targets their own Account
	 * @throws LastAdminException when disabling would leave zero enabled Admins
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	AccountEnabledChange setEnabled(UUID actingAdminId, UUID targetId, boolean enabled) {
		guardSelfAction(actingAdminId, targetId);
		LockedTarget locked = lockTarget(targetId);
		Account target = locked.account();
		boolean before = target.isEnabled();
		guardLastAdmin(locked, enabled, target.getRole());
		target.setEnabled(enabled);
		return new AccountEnabledChange(before, enabled);
	}

	/**
	 * Changes an Account's role between User and Admin. Ending the target's Sessions and auditing the
	 * change are the caller's job, once this has committed.
	 * @throws AccountNotFoundException when {@code targetId} is not an Account
	 * @throws SelfActionForbiddenException when the acting Admin targets their own Account
	 * @throws LastAdminException when the change would leave zero enabled Admins
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	AccountRoleChange changeRole(UUID actingAdminId, UUID targetId, Role role) {
		guardSelfAction(actingAdminId, targetId);
		LockedTarget locked = lockTarget(targetId);
		Account target = locked.account();
		Role before = target.getRole();
		guardLastAdmin(locked, target.isEnabled(), role);
		target.changeRole(role);
		return new AccountRoleChange(before, role);
	}

	private static void guardSelfAction(UUID actingAdminId, UUID targetId) {
		if (actingAdminId.equals(targetId)) {
			throw new SelfActionForbiddenException(targetId);
		}
	}

	/**
	 * Locks the target Account row for the change. When the target is a currently enabled Admin,
	 * every currently enabled Admin is locked alongside it (same query, same order, every time), so
	 * two concurrent changes that both need the last-Admin check always contend for that one lock set
	 * in the same order instead of deadlocking on each other's target row.
	 */
	private LockedTarget lockTarget(UUID targetId) {
		List<Account> enabledAdmins = accounts.findByRoleAndEnabledTrueOrderById(Role.ADMIN);
		Account target = enabledAdmins.stream()
			.filter((admin) -> admin.getId().equals(targetId))
			.findFirst()
			.or(() -> accounts.findForUpdateById(targetId))
			.orElseThrow(AccountNotFoundException::new);
		return new LockedTarget(target, enabledAdmins);
	}

	/** Rejects a change that would take the target's Account from the last enabled Admin to none. */
	private static void guardLastAdmin(LockedTarget locked, boolean afterEnabled, Role afterRole) {
		Account target = locked.account();
		boolean wasEnabledAdmin = target.getRole() == Role.ADMIN && target.isEnabled();
		boolean staysEnabledAdmin = afterRole == Role.ADMIN && afterEnabled;
		if (!wasEnabledAdmin || staysEnabledAdmin) {
			return;
		}
		boolean anotherEnabledAdminRemains = locked.enabledAdmins()
			.stream()
			.anyMatch((admin) -> !admin.getId().equals(target.getId()));
		if (!anotherEnabledAdminRemains) {
			throw new LastAdminException(target.getId());
		}
	}

	/** The target Account, row-locked, alongside every currently enabled Admin, locked the same way. */
	private record LockedTarget(Account account, List<Account> enabledAdmins) {
	}

}
