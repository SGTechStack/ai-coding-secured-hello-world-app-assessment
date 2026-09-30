package com.example.securedhello.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import tools.jackson.databind.json.JsonMapper;

/**
 * Builds RFC 9457 problem bodies carrying the stable snake_case {@code code} field that every API
 * error has. Used both by MVC exception handlers and by servlet filters that reject a request
 * before MVC runs.
 */
public final class ProblemResponses {

	public static final String CODE = "code";

	/** A new password breaks the Credential policy; the body lists the broken rules. */
	public static final String PASSWORD_POLICY = "password_policy";

	/** A new password is in the Account's Password History. */
	public static final String PASSWORD_HISTORY = "password_history";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private ProblemResponses() {
	}

	public static ProblemDetail problem(HttpStatus status, String code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setProperty(CODE, code);
		return problem;
	}

	/** 400 {@code password_policy} listing every broken Credential policy rule under {@code violations}. */
	public static ProblemDetail passwordPolicy(List<String> violations) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, PASSWORD_POLICY,
				"The password does not meet the password policy.");
		problem.setProperty("violations", violations);
		return problem;
	}

	/** 400 {@code password_history}: the password was used recently and may not be reused yet. */
	public static ProblemDetail passwordHistory() {
		return problem(HttpStatus.BAD_REQUEST, PASSWORD_HISTORY, "The password was used recently.");
	}

	/** Writes a problem body directly to the servlet response, for use outside Spring MVC. */
	public static void write(HttpServletResponse response, HttpStatus status, String code, String detail)
			throws IOException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", "about:blank");
		body.put("title", status.getReasonPhrase());
		body.put("status", status.value());
		body.put("detail", detail);
		body.put(CODE, code);
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		JSON.writeValue(response.getOutputStream(), body);
	}

}
