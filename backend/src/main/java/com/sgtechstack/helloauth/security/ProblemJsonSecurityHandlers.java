package com.sgtechstack.helloauth.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import com.sgtechstack.helloauth.audit.AuditEvent;
import com.sgtechstack.helloauth.audit.AuditLog;

/**
 * Writes Spring Security's 401 and 403 responses as problem details, matching the rest of the
 * API, instead of redirects or HTML error pages. Every 403 (a failed authorization or CSRF check)
 * is audited; 401s are not, because every anonymous page load produces one.
 */
@Component
public class ProblemJsonSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final JsonMapper jsonMapper;

	private final AuditLog audit;

	public ProblemJsonSecurityHandlers(JsonMapper jsonMapper, AuditLog audit) {
		this.jsonMapper = jsonMapper;
		this.audit = audit;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required.");
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		boolean csrf = accessDeniedException instanceof CsrfException;
		Authentication authentication = SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
		boolean anonymous = authentication == null || authentication instanceof AnonymousAuthenticationToken;
		this.audit.event(AuditEvent.ACCESS_DENIED)
			.actor(anonymous ? null : authentication.getName())
			.ip(request.getRemoteAddr())
			.reason(csrf ? "CSRF_INVALID" : "FORBIDDEN")
			.with("method", request.getMethod())
			.with("path", request.getRequestURI())
			.log();
		if (csrf) {
			write(request, response, HttpStatus.FORBIDDEN, "CSRF_INVALID", "Missing or invalid CSRF token.");
		}
		else {
			write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN",
					"You do not have permission to perform this action.");
		}
	}

	private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
			String detail) throws IOException {
		Map<String, Object> problem = new LinkedHashMap<>();
		problem.put("type", "about:blank");
		problem.put("title", status.getReasonPhrase());
		problem.put("status", status.value());
		problem.put("detail", detail);
		problem.put("instance", request.getRequestURI());
		problem.put("code", code);
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		this.jsonMapper.writeValue(response.getOutputStream(), problem);
	}

}
