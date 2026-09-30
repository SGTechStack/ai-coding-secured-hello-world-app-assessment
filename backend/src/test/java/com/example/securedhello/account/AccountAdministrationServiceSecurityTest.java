package com.example.securedhello.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * The Account administration service checks the Admin role itself, in addition to the URL rule,
 * so a future caller that bypasses {@code /api/admin/**} still cannot reach it as a User.
 */
@SpringBootTest
@ActiveProfiles("test")
class AccountAdministrationServiceSecurityTest {

	private static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000123");

	@Autowired
	AccountAdministrationService administration;

	@AfterEach
	void clearAuthentication() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void everyServiceMethodRequiresTheAdminRole() {
		assertThat(Arrays.stream(AccountAdministrationService.class.getDeclaredMethods())
			.filter((method) -> !Modifier.isPrivate(method.getModifiers()) && !method.isSynthetic()))
			.isNotEmpty()
			.allSatisfy((Method method) -> assertThat(method.getAnnotation(PreAuthorize.class))
				.as(method.getName())
				.isNotNull()
				.extracting(PreAuthorize::value)
				.isEqualTo("hasRole('ADMIN')"));
	}

	@Test
	void aUserIsRefusedByTheServiceItself() {
		authenticateAs(Role.USER);

		assertThatThrownBy(administration::list).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void anUnauthenticatedCallerIsRefusedByTheServiceItself() {
		assertThatThrownBy(administration::list).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
	}

	@Test
	void anAdminIsAllowed() {
		authenticateAs(Role.ADMIN);

		assertThat(administration.list()).isNotNull();
	}

	private static void authenticateAs(Role role) {
		SecurityContextHolder.getContext()
			.setAuthentication(new AccountPrincipal(ACCOUNT, "testuser123", role).toAuthentication());
	}

}
