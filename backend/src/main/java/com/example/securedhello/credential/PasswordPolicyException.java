package com.example.securedhello.credential;

import java.util.List;

/** A candidate password broke one or more Credential policy rules, all listed in {@link #violations()}. */
public class PasswordPolicyException extends RuntimeException {

	private final List<String> violations;

	PasswordPolicyException(List<String> violations) {
		super("Password does not meet the credential policy");
		this.violations = List.copyOf(violations);
	}

	/** The broken rules, for example {@code min_length} or {@code common_password}. */
	public List<String> violations() {
		return violations;
	}

}
