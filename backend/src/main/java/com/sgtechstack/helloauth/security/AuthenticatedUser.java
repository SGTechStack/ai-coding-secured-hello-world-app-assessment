package com.sgtechstack.helloauth.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.sgtechstack.helloauth.user.Role;

/**
 * The principal stored in the server-side session. Deliberately small and free of secrets: it
 * is serialized into the session store. {@link #getName()} is the username, which Spring
 * Session indexes so all of a user's sessions can be revoked.
 */
public record AuthenticatedUser(UUID id, String username, Role role) implements AuthenticatedPrincipal, Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Override
	public String getName() {
		return this.username;
	}

	public Authentication toAuthentication() {
		return UsernamePasswordAuthenticationToken.authenticated(this, null,
				List.of(new SimpleGrantedAuthority(this.role.authority())));
	}

}
