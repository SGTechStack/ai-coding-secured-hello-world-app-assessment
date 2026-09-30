package com.example.securedhello.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param basePath path prefix of every API endpoint, for example {@code /api}
 * @param maxRequestBodyBytes largest request body accepted before any controller runs; chunked bodies
 *        are buffered in memory up to this size, so it is capped at 10 MiB
 */
@Validated
@ConfigurationProperties("app.api")
public record ApiProperties(@Pattern(regexp = "^/[A-Za-z0-9/_-]*[A-Za-z0-9_-]$") String basePath,
		@Positive @Max(MAX_BODY_LIMIT) int maxRequestBodyBytes) {

	public static final int MAX_BODY_LIMIT = 10 * 1024 * 1024;

	public String path(String subPath) {
		return basePath + subPath;
	}

}
