package com.sgtechstack.helloauth.passwordreset;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sgtechstack.helloauth.security.ValidPassword;

@RestController
class PasswordResetController {

	static final String REQUEST_ACCEPTED_MESSAGE = "If an account exists for that email address, "
			+ "a password reset link has been sent.";

	private final PasswordResetService passwordResetService;

	PasswordResetController(PasswordResetService passwordResetService) {
		this.passwordResetService = passwordResetService;
	}

	@PostMapping("/api/auth/password-reset/request")
	@ResponseStatus(HttpStatus.ACCEPTED)
	MessageResponse requestReset(@Valid @RequestBody ResetRequest request, HttpServletRequest http) {
		this.passwordResetService.requestReset(request.email(), http.getRemoteAddr());
		return new MessageResponse(REQUEST_ACCEPTED_MESSAGE);
	}

	@PostMapping("/api/auth/password-reset/confirm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void confirmReset(@Valid @RequestBody ConfirmRequest request, HttpServletRequest http) {
		this.passwordResetService.confirm(request.token(), request.newPassword(), http.getRemoteAddr());
	}

	record ResetRequest(@NotBlank @Email @Size(max = 254) String email) {
	}

	record ConfirmRequest(@NotBlank @Size(max = 128) String token, @NotNull @ValidPassword String newPassword) {

		@Override
		public String toString() {
			return "ConfirmRequest[token=<redacted>, newPassword=<redacted>]";
		}

	}

	record MessageResponse(String message) {
	}

}
