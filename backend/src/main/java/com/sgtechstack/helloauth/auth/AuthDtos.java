package com.sgtechstack.helloauth.auth;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.sgtechstack.helloauth.security.AuthenticatedUser;
import com.sgtechstack.helloauth.security.ValidPassword;
import com.sgtechstack.helloauth.user.Identifiers;
import com.sgtechstack.helloauth.user.Role;
import com.sgtechstack.helloauth.user.User;

/**
 * Request and response bodies for the auth endpoints. Request records that carry a password
 * override toString so it can never reach a log line.
 */
final class AuthDtos {

	private AuthDtos() {
	}

	record RegisterRequest(
			@NotBlank @Pattern(regexp = Identifiers.USERNAME_PATTERN,
					message = "must be 3-32 characters: letters, digits, '.', '_' or '-'") String username,
			@NotBlank @Email @Size(max = 254) String email, @NotNull @ValidPassword String password) {

		@Override
		public String toString() {
			return "RegisterRequest[username=" + this.username + ", email=" + this.email + ", password=<redacted>]";
		}

	}

	record LoginRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 256) String password) {

		@Override
		public String toString() {
			return "LoginRequest[username=" + this.username + ", password=<redacted>]";
		}

	}

	record UserResponse(UUID id, String username, Role role) {

		static UserResponse from(AuthenticatedUser user) {
			return new UserResponse(user.id(), user.username(), user.role());
		}

		static UserResponse from(User user) {
			return new UserResponse(user.getId(), user.getUsername(), user.getRole());
		}

	}

	record CsrfResponse(String headerName, String token) {
	}

}
