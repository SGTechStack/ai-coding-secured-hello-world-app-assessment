package com.sgtechstack.helloauth.config;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param allowedOrigins exact frontend origins allowed to call the API with credentials; wildcards
 * are rejected because credentialed CORS must not trust arbitrary origins
 */
@Validated
@ConfigurationProperties("app.cors")
public record CorsProperties(@NotEmpty List<@NotBlank @Pattern(regexp = "^https?://[^*\\s/]+$",
		message = "must be an exact origin such as https://app.example.com") String> allowedOrigins) {
}
