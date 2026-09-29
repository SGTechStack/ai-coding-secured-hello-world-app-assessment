package com.sgtechstack.helloauth.config;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;

/**
 * Defines the session cookie explicitly. Without this bean, Spring Boot applies
 * {@code server.servlet.session.cookie.*} only on the embedded server; in a WAR deployment (and
 * in MockMvc tests) it silently takes the servlet container's defaults instead, which are not
 * HttpOnly. HttpOnly and SameSite are fixed here, not configurable.
 */
@Configuration
class SessionCookieConfig {

	@Bean
	CookieSerializer sessionCookieSerializer(SessionCookieProperties properties) {
		Assert.state(!properties.name().startsWith("__Host-") || properties.secure(),
				"A __Host- session cookie must be Secure; browsers reject it otherwise");
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName(properties.name());
		serializer.setCookiePath("/");
		serializer.setUseHttpOnlyCookie(true);
		serializer.setUseSecureCookie(properties.secure());
		serializer.setSameSite("Strict");
		return serializer;
	}

	/**
	 * @param name cookie name; use the {@code __Host-} prefix in production
	 * @param secure false only for local development over plain HTTP
	 */
	@Validated
	@ConfigurationProperties("app.session.cookie")
	record SessionCookieProperties(@NotBlank String name, boolean secure) {
	}

}
