package com.example.securedhello.account;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.securedhello.audit.AuditAction;
import com.example.securedhello.audit.AuditEvent;
import com.example.securedhello.audit.AuditLog;
import com.example.securedhello.web.ProblemResponses;

/**
 * {@code POST /register}: a Visitor creates an Account. Input formats are checked before any
 * business logic; a {@code role} in the body is not a field here, so it is ignored.
 */
@RestController
@RequestMapping("${app.api.base-path}")
class RegistrationController {

	/**
	 * Registration input. The password's length and byte limits are Credential policy rules, so
	 * they are reported as {@code password_policy} violations rather than here.
	 */
	record RegistrationRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9]{3,32}") String username,
			@NotBlank @Size(max = 254) @Email @Pattern(regexp = "[^@\\s]+@[^@\\s]+\\.[^@\\s]+") String email,
			@NotNull String password) {
	}

	private final RegistrationService registration;

	private final AuditLog auditLog;

	RegistrationController(RegistrationService registration, AuditLog auditLog) {
		this.registration = registration;
		this.auditLog = auditLog;
	}

	@PostMapping("/register")
	ResponseEntity<Void> register(@Valid @RequestBody RegistrationRequest body, HttpServletRequest request) {
		UUID accountId = registration.register(body.username(), body.email(), body.password());
		auditLog.record(AuditEvent.success(AuditAction.USER_PROVISIONING).userId(accountId).request(request));
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	/** One combined error for a taken username or email, so neither is disclosed. */
	@ExceptionHandler(UserExistException.class)
	ProblemDetail userExist(HttpServletRequest request) {
		auditLog.record(AuditEvent.failure(AuditAction.USER_PROVISIONING, "user_exist")
			.request(request)
			.sessionHashOf(request));
		return ProblemResponses.problem(HttpStatus.BAD_REQUEST, "user_exist", "user exist");
	}

}
