package com.example.securedhello.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Session cookie attributes, set explicitly so they hold in every deployment mode (Boot only maps
 * {@code server.servlet.session.cookie.*} onto Spring Session when running an embedded server).
 * Secure is on everywhere except the {@code dev} profile.
 */
@Configuration
class SessionCookieConfig {

	@Bean
	DefaultCookieSerializer cookieSerializer(@Value("${app.session.cookie-secure}") boolean secure) {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName("SESSION");
		serializer.setCookiePath("/");
		serializer.setUseHttpOnlyCookie(true);
		serializer.setSameSite("Lax");
		serializer.setUseSecureCookie(secure);
		return serializer;
	}

}
