package com.example.securedhello.account;

import java.util.Locale;
import java.util.concurrent.Executor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.securedhello.credential.PasswordHistoryException;
import com.example.securedhello.credential.PasswordPolicyException;
import com.example.securedhello.logging.CorrelationFilter;
import com.example.securedhello.notification.EmailService;
import com.example.securedhello.ratelimit.ClientAddressLimit;
import com.example.securedhello.ratelimit.RateLimitedByClientAddress;
import com.example.securedhello.ratelimit.RateLimiters;
import com.example.securedhello.security.SessionControl;
import com.example.securedhello.web.ErrorCategory;
import com.example.securedhello.web.ProblemResponses;

/**
 * {@code POST /password-reset/request} and {@code POST /password-reset/confirm}. The request
 * endpoint always answers 202 with a generic message, before any lookup: token issuance and the
 * email run on a background executor, so response time never depends on whether the Account exists,
 * is enabled, or was rate-limited by email vs. address (that refusal is still synchronous, since it
 * never depends on the Account either). Confirmation ends every Session of the redeemed token's
 * Account and leaves any active lock in place.
 */
@RestController
@RequestMapping("${app.api.base-path}")
class PasswordResetController {

	static final String PASSWORD_RESET = "password_reset";

	private static final Logger log = LoggerFactory.getLogger(PasswordResetController.class);

	/**
	 * The one 202 body for every request, whether or not the email is registered (story 51): it never
	 * says which.
	 */
	private static final ResetAccepted ACCEPTED = new ResetAccepted(
			"If an Account exists for that email, a reset link has been sent.");

	/** Input formats are checked before any business logic; the email's length and format mirror registration. */
	record ResetRequest(@NotBlank @Size(max = 254) @Email @Pattern(regexp = "[^@\\s]+@[^@\\s]+\\.[^@\\s]+") String email) {
	}

	/** The new password's length and byte limits are Credential policy rules, not checked here. */
	record ResetConfirmRequest(@NotBlank String token, @NotNull String newPassword) {
	}

	record ResetAccepted(String message) {
	}

	private final PasswordResetService passwordReset;

	private final SessionControl sessionControl;

	private final EmailService emailService;

	private final RateLimiters rateLimiters;

	private final Executor passwordResetExecutor;

	PasswordResetController(PasswordResetService passwordReset, SessionControl sessionControl,
			EmailService emailService, RateLimiters rateLimiters, Executor passwordResetExecutor) {
		this.passwordReset = passwordReset;
		this.sessionControl = sessionControl;
		this.emailService = emailService;
		this.rateLimiters = rateLimiters;
		this.passwordResetExecutor = passwordResetExecutor;
	}

	/**
	 * The per-address limit is acquired before the body is validated, so a malformed request spends
	 * quota too (issue 18); the per-email limit needs the validated email, so it runs here.
	 */
	@PostMapping("/password-reset/request")
	@RateLimitedByClientAddress(ClientAddressLimit.RESET_REQUEST)
	ResponseEntity<ResetAccepted> request(@Valid @RequestBody ResetRequest body, HttpServletRequest request) {
		String email = body.email().toLowerCase(Locale.ROOT);
		rateLimiters.resetRequestByEmail().acquire(email);
		String httpMethod = request.getMethod();
		String urlPath = CorrelationFilter.urlPath(request);
		passwordResetExecutor.execute(() -> issueSafely(email, httpMethod, urlPath));
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ACCEPTED);
	}

	/**
	 * Runs on the background executor, with no request thread left to catch a failure: logged here,
	 * the same way the global handler logs an unexpected exception, rather than left to the executor's
	 * default (unstructured, unsanitised) uncaught-exception handling.
	 */
	private void issueSafely(String email, String httpMethod, String urlPath) {
		try {
			passwordReset.issue(email, httpMethod, urlPath);
		}
		catch (RuntimeException ex) {
			ErrorCategory category = ErrorCategory.of(ex);
			log.atError()
				.setCause(ex)
				.addKeyValue("error_code", "password_reset_issuance_failed")
				.addKeyValue("error_category", category.value())
				.addKeyValue("error_follow_up_action", category.followUpAction())
				.log("Unexpected exception while issuing a password reset");
		}
	}

	/** Rate-limited before the body is validated, so a malformed confirmation spends quota too (issue 18). */
	@PostMapping("/password-reset/confirm")
	@RateLimitedByClientAddress(ClientAddressLimit.RESET_CONFIRM)
	ResponseEntity<Void> confirm(@Valid @RequestBody ResetConfirmRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		PasswordResetService.ConfirmedReset confirmed = passwordReset.confirm(body.token(), body.newPassword(),
				request.getMethod(), CorrelationFilter.urlPath(request));
		sessionControl.endAll(confirmed.accountId(), PASSWORD_RESET, request, response);
		emailService.notifyPasswordResetCompleted(confirmed.email());
		return ResponseEntity.ok().build();
	}

	/**
	 * {@link PasswordResetService} has already audited every failure below as {@code password-reset},
	 * with the resolved Account where one was resolved. These three handlers exist only to shape the
	 * response body: defining them here, rather than relying on the global handler, keeps the global
	 * handler's own (differently audited) handling of the same exception types out of this endpoint.
	 */
	@ExceptionHandler(TokenInvalidException.class)
	ProblemDetail tokenInvalid() {
		return ProblemResponses.problem(HttpStatus.BAD_REQUEST, "token_invalid",
				"password reset token expired or invalid");
	}

	@ExceptionHandler(PasswordPolicyException.class)
	ProblemDetail passwordPolicy(PasswordPolicyException exception) {
		return ProblemResponses.passwordPolicy(exception.violations());
	}

	@ExceptionHandler(PasswordHistoryException.class)
	ProblemDetail passwordHistory() {
		return ProblemResponses.passwordHistory();
	}

}
