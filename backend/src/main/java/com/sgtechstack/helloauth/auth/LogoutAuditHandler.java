package com.sgtechstack.helloauth.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import com.sgtechstack.helloauth.audit.AuditEvent;
import com.sgtechstack.helloauth.audit.AuditLog;

@Component
public class LogoutAuditHandler implements LogoutHandler {

	private final AuditLog audit;

	LogoutAuditHandler(AuditLog audit) {
		this.audit = audit;
	}

	@Override
	public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
		if (authentication != null) {
			this.audit.event(AuditEvent.LOGOUT).actor(authentication.getName()).ip(request.getRemoteAddr()).log();
		}
	}

}
