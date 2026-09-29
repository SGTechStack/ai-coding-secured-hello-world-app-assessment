package com.sgtechstack.helloauth.admin;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sgtechstack.helloauth.admin.AdminDtos.AdminUserResponse;
import com.sgtechstack.helloauth.admin.AdminDtos.UpdateRoleRequest;
import com.sgtechstack.helloauth.admin.AdminDtos.UpdateStatusRequest;
import com.sgtechstack.helloauth.admin.AdminDtos.UserPage;
import com.sgtechstack.helloauth.security.AuthenticatedUser;

/**
 * Admin-only. Access is enforced twice: by URL in SecurityConfig and by {@code @PreAuthorize}
 * here, so neither a routing change nor a config change alone can expose these endpoints.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class AdminUserController {

	private final AdminUserService adminUserService;

	AdminUserController(AdminUserService adminUserService) {
		this.adminUserService = adminUserService;
	}

	@GetMapping
	UserPage list(@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return this.adminUserService.list(page, size);
	}

	@PatchMapping("/{id}/status")
	AdminUserResponse updateStatus(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
			@Valid @RequestBody UpdateStatusRequest request, HttpServletRequest http) {
		return this.adminUserService.setEnabled(actor, id, request.enabled(), http.getRemoteAddr());
	}

	@PatchMapping("/{id}/role")
	AdminUserResponse updateRole(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
			@Valid @RequestBody UpdateRoleRequest request, HttpServletRequest http) {
		return this.adminUserService.changeRole(actor, id, request.role(), http.getRemoteAddr());
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id, HttpServletRequest http) {
		this.adminUserService.delete(actor, id, http.getRemoteAddr());
	}

}
