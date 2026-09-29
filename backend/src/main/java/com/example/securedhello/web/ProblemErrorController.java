package com.example.securedhello.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Boot's default {@code /error} handler for failures that happen outside Spring MVC (for
 * example in a servlet filter), so they also get a ProblemDetail with a {@code code} and no
 * internals.
 */
@RestController
class ProblemErrorController implements ErrorController {

	@RequestMapping("${server.error.path:/error}")
	ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
		Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		HttpStatus status = (statusAttribute instanceof Integer code) ? HttpStatus.resolve(code) : null;
		if (status == null || !status.isError()) {
			status = HttpStatus.INTERNAL_SERVER_ERROR;
		}
		String detail = status.is5xxServerError() ? GlobalExceptionHandler.INTERNAL_ERROR_DETAIL
				: status.getReasonPhrase();
		return ResponseEntity.status(status)
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.body(ProblemResponses.problem(status, GlobalExceptionHandler.codeFor(status), detail));
	}

}
