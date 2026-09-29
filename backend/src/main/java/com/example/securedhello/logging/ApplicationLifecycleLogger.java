package com.example.securedhello.logging;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.example.securedhello.audit.AuditAction;
import com.example.securedhello.audit.AuditEvent;
import com.example.securedhello.audit.AuditLog;
import com.example.securedhello.config.ApiProperties;
import com.example.securedhello.config.LockoutProperties;
import com.example.securedhello.config.RateLimitProperties;
import com.example.securedhello.config.SessionProperties;

/**
 * Startup and shutdown events. Once the app is ready it logs the host, active profiles and key
 * non-secret settings (service metadata is on every line), and audits {@code application-startup}.
 * Never logs credentials, connection strings or database names. A shutdown event and an
 * {@code application-shutdown} audit event are emitted when the context closes.
 */
@Component
class ApplicationLifecycleLogger {

	private static final Logger log = LoggerFactory.getLogger(ApplicationLifecycleLogger.class);

	private final Environment environment;

	private final ApiProperties api;

	private final SessionProperties session;

	private final LockoutProperties lockout;

	private final RateLimitProperties rateLimit;

	private final AuditLog auditLog;

	ApplicationLifecycleLogger(Environment environment, ApiProperties api, SessionProperties session,
			LockoutProperties lockout, RateLimitProperties rateLimit, AuditLog auditLog) {
		this.environment = environment;
		this.api = api;
		this.session = session;
		this.lockout = lockout;
		this.rateLimit = rateLimit;
		this.auditLog = auditLog;
	}

	@EventListener(ApplicationReadyEvent.class)
	void started() {
		InetAddress host = localHost();
		log.atInfo()
			.addKeyValue("host.name", host.getHostName())
			.addKeyValue("host.ip", host.getHostAddress())
			.addKeyValue("spring.profiles.active", List.of(environment.getActiveProfiles()))
			.addKeyValue("config.api.base_path", api.basePath())
			.addKeyValue("config.api.max_request_body_bytes", api.maxRequestBodyBytes())
			.addKeyValue("config.session.cookie_secure",
					environment.getProperty("app.session.cookie-secure", Boolean.class))
			.addKeyValue("config.session.timeout.idle", session.idleTimeout().toString())
			.addKeyValue("config.session.timeout.absolute", session.absoluteTimeout().toString())
			.addKeyValue("config.session.max_concurrent_per_account", session.maxConcurrentPerAccount())
			.addKeyValue("config.lockout.threshold", lockout.threshold())
			.addKeyValue("config.lockout.duration", lockout.duration().toString())
			.addKeyValue("config.rate_limit.login.capacity", rateLimit.login().capacity())
			.addKeyValue("config.rate_limit.login.period", rateLimit.login().period().toString())
			.log("Application started");
		auditLog.record(AuditEvent.success(AuditAction.APPLICATION_STARTUP));
	}

	@EventListener(ContextClosedEvent.class)
	void stopping() {
		log.atInfo().log("Application stopping");
		auditLog.record(AuditEvent.success(AuditAction.APPLICATION_SHUTDOWN));
	}

	private static InetAddress localHost() {
		try {
			return InetAddress.getLocalHost();
		}
		catch (UnknownHostException ex) {
			return InetAddress.getLoopbackAddress();
		}
	}

}
