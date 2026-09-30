package com.example.securedhello.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CSRF bootstrap (ADR 0002). The token lives in the server-side Session and is handed to the SPA
 * only through this uncached response, never through a cookie.
 */
@RestController
@RequestMapping("${app.api.base-path}")
class CsrfController {

	record CsrfResponse(String headerName, String token) {
	}

	@GetMapping("/csrf")
	ResponseEntity<CsrfResponse> csrf(CsrfToken csrfToken) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.header("Pragma", "no-cache")
			.body(new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getToken()));
	}

}
