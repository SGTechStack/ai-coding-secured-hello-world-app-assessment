package com.sgtechstack.helloauth.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import org.springframework.data.domain.Page;

import com.sgtechstack.helloauth.user.Role;
import com.sgtechstack.helloauth.user.User;

final class AdminDtos {

	private AdminDtos() {
	}

	/** What an admin may see about an account; never the password hash or lockout internals. */
	record AdminUserResponse(UUID id, String username, String email, Role role, boolean enabled, Instant createdAt) {

		static AdminUserResponse from(User user) {
			return new AdminUserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole(),
					user.isEnabled(), user.getCreatedAt());
		}

	}

	record UserPage(List<AdminUserResponse> items, int page, int size, long totalItems, int totalPages) {

		static UserPage from(Page<User> page) {
			return new UserPage(page.map(AdminUserResponse::from).getContent(), page.getNumber(), page.getSize(),
					page.getTotalElements(), page.getTotalPages());
		}

	}

	record UpdateStatusRequest(@NotNull Boolean enabled) {
	}

	record UpdateRoleRequest(@NotNull Role role) {
	}

}
