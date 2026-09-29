package com.example.securedhello.account;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

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

}
