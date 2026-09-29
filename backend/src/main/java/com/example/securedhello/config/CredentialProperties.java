package com.example.securedhello.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the Credential policy.
 *
 * @param minLength fewest characters a password may have; at least 12
 * @param maxLength most characters a password may have; at most 64, because BCrypt reads only
 *        72 bytes
 * @param bcryptCost BCrypt work factor; at least 12
 * @param commonPasswords list of common passwords, one per line, {@code #} comments allowed
 * @param historyLength how many of an Account's latest passwords, the current one included, its
 *        Password History keeps and refuses to reuse; at least 1
 */
@Validated
@ConfigurationProperties("app.credential")
public record CredentialProperties(@Min(12) int minLength, @Max(64) int maxLength, @Min(12) @Max(31) int bcryptCost,
		@NotNull Resource commonPasswords, @Min(1) int historyLength) {
}
