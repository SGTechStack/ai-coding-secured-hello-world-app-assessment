package com.sgtechstack.helloauth.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sgtechstack.helloauth.auth.AuthDtos.CsrfResponse;
import com.sgtechstack.helloauth.auth.AuthDtos.LoginRequest;
import com.sgtechstack.helloauth.auth.AuthDtos.RegisterRequest;
import com.sgtechstack.helloauth.auth.AuthDtos.UserResponse;
import com.sgtechstack.helloauth.security.AuthenticatedUser;
import com.sgtechstack.helloauth.security.SessionLogin;

/**
 * Logout is handled by Spring Security's logout filter at POST /api/auth/logout.
 */
@RestController
class AuthController {

	private final RegistrationService registrationService;

	private final LoginService loginService;

	private final SessionLogin sessionLogin;

	AuthController(RegistrationService registrationService, LoginService loginService, SessionLogin sessionLogin) {
		this.registrationService = registrationService;
		this.loginService = loginService;
		this.sessionLogin = sessionLogin;
	}

	/**
	 * The CSRF token for this session, for the SPA to send back in the header. CORS stops other
	 * origins from reading it.
	 */
	@GetMapping("/api/auth/csrf")
	CsrfResponse csrf(CsrfToken token) {
		return new CsrfResponse(token.getHeaderName(), token.getToken());
	}

	@PostMapping("/api/auth/register")
	@ResponseStatus(HttpStatus.CREATED)
	UserResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
		return UserResponse.from(this.registrationService.register(request.username(), request.email(),
				request.password(), http.getRemoteAddr()));
	}

	@PostMapping("/api/auth/login")
	UserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http,
			HttpServletResponse response) {
		AuthenticatedUser user = this.loginService.authenticate(request.username(), request.password(),
				http.getRemoteAddr());
		this.sessionLogin.establish(user, http, response);
		return UserResponse.from(user);
	}

	@GetMapping("/api/me")
	UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
		return UserResponse.from(user);
	}

}
