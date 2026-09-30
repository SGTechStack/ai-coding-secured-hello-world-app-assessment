package com.example.securedhello.account;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account administration for Admins. Every method checks the Admin role itself with
 * {@code @PreAuthorize}, in addition to the {@code /api/admin/**} URL rule, so no caller reaches it
 * as a User. Each change publishes an {@link AccountEvent}, so ending the target's Sessions and
 * auditing the change follow once it has committed ({@link AccountEventListener}).
 */
@Service
class AccountAdministrationService {

	private final AccountRepository accounts;

	private final DeletedAccountRepository deletedAccounts;

	private final PasswordResetTokenRepository resetTokens;

	private final PasswordHistoryRepository passwordHistory;

	private final Clock clock;

	private final ApplicationEventPublisher events;

	AccountAdministrationService(AccountRepository accounts, DeletedAccountRepository deletedAccounts,
			PasswordResetTokenRepository resetTokens, PasswordHistoryRepository passwordHistory, Clock clock,
			ApplicationEventPublisher events) {
		this.accounts = accounts;
		this.deletedAccounts = deletedAccounts;
		this.resetTokens = resetTokens;
		this.passwordHistory = passwordHistory;
		this.clock = clock;
		this.events = events;
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
	 * not treated as a suspected compromise (ADR 0001). Disabling ends the target's Sessions once this
	 * has committed; re-enabling does not.
	 * @throws AccountNotFoundException when {@code targetId} is not an Account
	 * @throws SelfActionForbiddenException when the acting Admin targets their own Account
	 * @throws LastAdminException when disabling would leave zero enabled Admins
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	void setEnabled(UUID actingAdminId, UUID targetId, boolean enabled) {
		guardSelfAction(actingAdminId, targetId);
		LockedTarget locked = lockTarget(targetId);
		Account target = locked.account();
		boolean before = target.isEnabled();
		guardLastAdmin(locked, enabled, target.getRole());
		target.setEnabled(enabled);
		events.publishEvent(new AccountEvent.EnabledChanged(actingAdminId, targetId, before, enabled));
	}

	/**
	 * Changes an Account's role between User and Admin, ending the target's Sessions once this has
	 * committed.
	 * @throws AccountNotFoundException when {@code targetId} is not an Account
	 * @throws SelfActionForbiddenException when the acting Admin targets their own Account
	 * @throws LastAdminException when the change would leave zero enabled Admins
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	void changeRole(UUID actingAdminId, UUID targetId, Role role) {
		guardSelfAction(actingAdminId, targetId);
		LockedTarget locked = lockTarget(targetId);
		Account target = locked.account();
		Role before = target.getRole();
		guardLastAdmin(locked, target.isEnabled(), role);
		target.changeRole(role);
		events.publishEvent(new AccountEvent.RoleChanged(actingAdminId, targetId, before, role));
	}

	/**
	 * Lifts a Lock on an Account, clearing {@code locked_until} and the failure counter so a fresh
	 * threshold of wrong passwords is needed to lock it again. Not subject to the last-Admin rule: an
	 * unlock never changes {@code enabled} or the role. Idempotent: unlocking an Account that is not
	 * currently Locked still succeeds.
	 * @throws AccountNotFoundException when {@code targetId} is not an Account
	 * @throws SelfActionForbiddenException when the acting Admin targets their own Account, so a
	 *         hijacked admin Session can't lift a lock that is protecting it
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	void unlock(UUID actingAdminId, UUID targetId) {
		guardSelfAction(actingAdminId, targetId);
		Account target = accounts.findForUpdateById(targetId).orElseThrow(AccountNotFoundException::new);
		boolean before = target.isLocked(clock.instant());
		target.unlock();
		events.publishEvent(new AccountEvent.Unlocked(actingAdminId, targetId, before));
	}

	/**
	 * Requires the Account to change its password before it can do anything else, because an Admin
	 * suspects it is compromised. Not subject to the self-action guard: an Admin may require it of
	 * themselves, and the Bootstrap Admin starts out that way. Not subject to the last-Admin rule
	 * either: the requirement never changes {@code enabled} or the role. Idempotent. Ends the target's
	 * Sessions once this has committed, so a suspected attacker is logged out.
	 * @throws AccountNotFoundException when {@code targetId} is not an Account
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	void requirePasswordChange(UUID actingAdminId, UUID targetId) {
		Account target = accounts.findForUpdateById(targetId).orElseThrow(AccountNotFoundException::new);
		boolean alreadyRequired = target.requirePasswordChange();
		events.publishEvent(new AccountEvent.PasswordChangeRequired(actingAdminId, targetId, alreadyRequired));
	}

	/**
	 * Deletes an Account. In one transaction it writes the Account's tombstone, removes its Reset
	 * Tokens and Password History, and removes the Account itself; the tombstone is then the only
	 * record of it, and keeps its UUID, username, email, deletion time and the deleting Admin
	 * indefinitely (ADR 0001). Because the tombstone holds the username for good, that username can
	 * never be registered or bootstrapped again; the email can be reused. Ends the target's Sessions
	 * once this has committed.
	 * @throws AccountNotFoundException when {@code targetId} is not an Account
	 * @throws SelfActionForbiddenException when the acting Admin targets their own Account
	 * @throws LastAdminException when the deletion would leave zero enabled Admins
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	void delete(UUID actingAdminId, UUID targetId) {
		guardSelfAction(actingAdminId, targetId);
		LockedTarget locked = lockTarget(targetId);
		Account target = locked.account();
		// A deleted Account is neither enabled nor an Admin afterwards, because it is gone.
		guardLastAdmin(locked, false, target.getRole());
		deletedAccounts.save(new DeletedAccount(target, actingAdminId, clock.instant()));
		resetTokens.deleteByUserId(targetId);
		passwordHistory.deleteByUserId(targetId);
		// Both tables hold a foreign key to the Account, so their rows are written away before it is.
		accounts.flush();
		accounts.delete(target);
		accounts.flush();
		events.publishEvent(new AccountEvent.Deleted(actingAdminId, targetId));
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
