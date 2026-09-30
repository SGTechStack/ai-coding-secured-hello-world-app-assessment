package com.example.securedhello.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Acquires the limiter named by {@link RateLimitedByClientAddress} before Spring MVC resolves the
 * handler's arguments. {@code @Valid @RequestBody} binding, and the global handler's
 * {@code validation} audit event for a body that fails it, both happen during argument resolution,
 * so a limiter acquired inside the handler body never sees a malformed request. Acquiring it here
 * means every request, well-formed or not, spends quota, and the {@code validation} audit events a
 * caller can cause are bounded by the limiter (issue 18).
 * <p>
 * A refusal is thrown as {@link RateLimitExceededException}; the dispatcher hands it to the same
 * exception handler as a refusal from inside the handler, so the 429 body, {@code Retry-After} and
 * {@code rate_limited} audit event are unchanged.
 */
@Component
class RateLimitBeforeBindingInterceptor implements HandlerInterceptor, WebMvcConfigurer {

	private final RateLimiters rateLimiters;

	RateLimitBeforeBindingInterceptor(RateLimiters rateLimiters) {
		this.rateLimiters = rateLimiters;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this);
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (handler instanceof HandlerMethod method) {
			RateLimitedByClientAddress limit = method.getMethodAnnotation(RateLimitedByClientAddress.class);
			if (limit != null) {
				limit.value().in(this.rateLimiters).acquire(request.getRemoteAddr());
			}
		}
		return true;
	}

}
