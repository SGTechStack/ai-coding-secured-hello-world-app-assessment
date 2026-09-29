package com.example.securedhello.credential;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.securedhello.config.CredentialProperties;

/**
 * The Credential policy: decides whether a candidate password is acceptable and hashes accepted
 * ones, and compares login passwords with stored hashes. Used by registration and login, and later
 * by Password Change, reset confirmation and the Bootstrap Admin.
 * <p>
 * Rules, each reported by name when broken: {@code min_length}, {@code max_length} (characters),
 * {@code max_bytes} (72 UTF-8 bytes, all BCrypt reads), {@code uppercase}, {@code lowercase},
 * {@code digit}, {@code special} (any printable character that is not a letter or digit, whitespace
 * included; Unicode letters and digits count as letters and digits), and {@code common_password}
 * (the bundled list, compared case-insensitively).
 */
@Component
public class CredentialPolicy {

	/** BCrypt ignores input beyond 72 bytes, so longer passwords are refused rather than truncated. */
	public static final int MAX_BYTES = 72;

	private final int minLength;

	private final int maxLength;

	private final Set<String> commonPasswords;

	/** Compared against when there is no stored hash to compare with, to keep timing uniform. */
	private static final String DUMMY_INPUT = "no-account-dummy-password";

	private final PasswordEncoder encoder;

	/** Same cost as real hashes, so comparing against it takes as long. */
	private final String dummyHash;

	public CredentialPolicy(CredentialProperties properties) {
		this.minLength = properties.minLength();
		this.maxLength = properties.maxLength();
		this.commonPasswords = load(properties.commonPasswords());
		this.encoder = new BCryptPasswordEncoder(properties.bcryptCost());
		this.dummyHash = encoder.encode(DUMMY_INPUT);
	}

	/** Every rule the password breaks, in a stable order; empty when it is acceptable. */
	public List<String> violations(String password) {
		List<String> violations = new ArrayList<>();
		int length = password.codePointCount(0, password.length());
		if (length < minLength) {
			violations.add("min_length");
		}
		if (length > maxLength) {
			violations.add("max_length");
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
			violations.add("max_bytes");
		}
		CharacterClasses classes = CharacterClasses.of(password);
		if (!classes.upper) {
			violations.add("uppercase");
		}
		if (!classes.lower) {
			violations.add("lowercase");
		}
		if (!classes.digit) {
			violations.add("digit");
		}
		if (!classes.special) {
			violations.add("special");
		}
		if (commonPasswords.contains(password.toLowerCase(Locale.ROOT))) {
			violations.add("common_password");
		}
		return violations;
	}

	/**
	 * Refuses a password that breaks any rule.
	 * @throws PasswordPolicyException listing every broken rule
	 */
	public void check(String password) {
		List<String> violations = violations(password);
		if (!violations.isEmpty()) {
			throw new PasswordPolicyException(violations);
		}
	}

	/** BCrypt hash of a password that has passed {@link #check}. */
	public String hash(String password) {
		return encoder.encode(password);
	}

	/**
	 * Whether the password matches a stored hash. A password over {@link #MAX_BYTES} can never have
	 * been accepted, so it never matches, but it still costs one BCrypt comparison.
	 */
	public boolean matches(String password, String hash) {
		if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
			encoder.matches(DUMMY_INPUT, hash);
			return false;
		}
		return encoder.matches(password, hash);
	}

	/**
	 * Runs one BCrypt comparison against a fixed dummy hash and returns {@code false}, so a login
	 * for an unknown username takes as long as one with a wrong password.
	 */
	public boolean matchesNoAccount(String password) {
		matches(password, dummyHash);
		return false;
	}

	private static Set<String> load(Resource resource) {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
			return reader.lines()
				.map(String::strip)
				.filter((line) -> !line.isEmpty() && !line.startsWith("#"))
				.map((line) -> line.toLowerCase(Locale.ROOT))
				.collect(Collectors.toUnmodifiableSet());
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot read the common-password list", ex);
		}
	}

	private static final class CharacterClasses {

		private boolean upper;

		private boolean lower;

		private boolean digit;

		private boolean special;

		static CharacterClasses of(String password) {
			CharacterClasses classes = new CharacterClasses();
			password.codePoints().forEach(classes::add);
			return classes;
		}

		private void add(int codePoint) {
			if (Character.isUpperCase(codePoint)) {
				upper = true;
			}
			else if (Character.isLowerCase(codePoint)) {
				lower = true;
			}
			else if (Character.isDigit(codePoint)) {
				digit = true;
			}
			else if (!Character.isLetter(codePoint) && isPrintable(codePoint)) {
				special = true;
			}
		}

		private static boolean isPrintable(int codePoint) {
			return switch (Character.getType(codePoint)) {
				case Character.CONTROL, Character.FORMAT, Character.UNASSIGNED, Character.SURROGATE,
						Character.PRIVATE_USE, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR ->
					false;
				default -> true;
			};
		}

	}

}
