package com.example.securedhello.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.securedhello.audit.AuditAction;
import com.example.securedhello.audit.AuditEvent;
import com.example.securedhello.audit.AuditLog;
import com.example.securedhello.config.PasswordResetProperties;
import com.example.securedhello.credential.PasswordHistoryException;
import com.example.securedhello.notification.EmailService;

/**
 * Password reset service: issues a Reset Token (at least 32 random bytes, URL-safe encoded), stores
 * only its SHA-256 hash with a configurable expiry, and cancels any earlier pending token for the
 * Account before issuing the new one. Redeems a token exactly once, applying the same Credential
 * policy and Password History rules as Password Change. Issues nothing, and sends no email, for an
 * unknown or Disabled Account, but always looks the same to the caller: {@link #issue} runs on a
 * background executor so the reset-request endpoint can return 202 before any lookup (spec).
 */
@Service
class PasswordResetService {

	private static final SecureRandom RANDOM = new SecureRandom();

	/** At least 32 random bytes, URL-safe Base64 encoded, per spec. */
	private static final int TOKEN_BYTES = 32;

	private final AccountRepository accounts;

	private final PasswordResetTokenRepository tokens;

	private final PasswordResetTokenCanceller resetTokenCanceller;

	private final PasswordUpdater passwordUpdater;

	private final PasswordResetProperties properties;

	private final EmailService emailService;

	private final AuditLog auditLog;

	private final Clock clock;

	PasswordResetService(AccountRepository accounts, PasswordResetTokenRepository tokens,
			PasswordResetTokenCanceller resetTokenCanceller, PasswordUpdater passwordUpdater,
			PasswordResetProperties properties, EmailService emailService, AuditLog auditLog, Clock clock) {
		this.accounts = accounts;
		this.tokens = tokens;
		this.resetTokenCanceller = resetTokenCanceller;
		this.passwordUpdater = passwordUpdater;
		this.properties = properties;
		this.emailService = emailService;
		this.auditLog = auditLog;
		this.clock = clock;
	}

	/**
	 * Issues a Reset Token and sends its link, unless the email matches no Account or a Disabled one.
	 * Runs on the background executor (see {@code PasswordResetExecutorConfig}), after the endpoint
	 * has already returned 202, so it takes {@code httpMethod} and {@code urlPath} rather than a live
	 * {@code HttpServletRequest} to audit issuance.
	 */
	@Transactional
	void issue(String email, String httpMethod, String urlPath) {
		Optional<Account> found = accounts.findByEmail(email.toLowerCase(Locale.ROOT));
		if (found.isEmpty() || !found.get().isEnabled()) {
			return;
		}
		Account account = found.get();
		Instant now = clock.instant();
		resetTokenCanceller.cancelPending(account.getId(), now);
		String token = generateToken();
		tokens.save(PasswordResetToken.issue(account.getId(), hash(token), now.plus(properties.tokenExpiry())));
		String link = properties.frontendOrigin() + "/reset-password#token=" + token;
		emailService.sendPasswordResetLink(account.getEmail(), link);
		auditLog.record(
				AuditEvent.success(AuditAction.PASSWORD_RESET).userId(account.getId()).request(httpMethod, urlPath));
	}

	/**
	 * Redeems a Reset Token: applies the Credential policy and Password History, marks the token
	 * used, and cancels any other pending token, but never touches an active lock (story 58).
	 * @throws TokenInvalidException when the token is unknown, expired or already used
	 * @throws com.example.securedhello.credential.PasswordPolicyException when the new password
	 *         breaks the Credential policy
	 * @throws com.example.securedhello.credential.PasswordHistoryException when the new password is
	 *         the current one or another in the Password History
	 */
	@Transactional
	ConfirmedReset confirm(String token, String newPassword, String httpMethod, String urlPath) {
		Instant now = clock.instant();
		PasswordResetToken resetToken = tokens.findByTokenHash(hash(token))
			.filter((candidate) -> candidate.isUsable(now))
			.orElseThrow(() -> invalid(httpMethod, urlPath));
		Account account = accounts.findForUpdateById(resetToken.getUserId())
			.orElseThrow(() -> invalid(httpMethod, urlPath));
		try {
			passwordUpdater.apply(account, newPassword, now);
		}
		catch (RuntimeException ex) {
			auditLog.record(AuditEvent.failure(AuditAction.PASSWORD_RESET, reasonFor(ex))
				.userId(account.getId())
				.request(httpMethod, urlPath));
			throw ex;
		}
		resetToken.markUsed(now);
		resetTokenCanceller.cancelPending(account.getId(), now);
		auditLog.record(
				AuditEvent.success(AuditAction.PASSWORD_RESET).userId(account.getId()).request(httpMethod, urlPath));
		return new ConfirmedReset(account.getId(), account.getEmail());
	}

	/** The Account resolved by a redeemed token, and its email for the completion notification. */
	record ConfirmedReset(UUID accountId, String email) {
	}

	private TokenInvalidException invalid(String httpMethod, String urlPath) {
		auditLog.record(AuditEvent.failure(AuditAction.PASSWORD_RESET, "token_invalid").request(httpMethod, urlPath));
		return new TokenInvalidException();
	}

	private static String reasonFor(RuntimeException ex) {
		return (ex instanceof PasswordHistoryException) ? "password_history" : "password_policy";
	}

	private static String generateToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required by every Java platform", ex);
		}
	}

}
