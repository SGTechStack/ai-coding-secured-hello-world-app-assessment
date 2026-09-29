package com.eitri.config;

import java.util.Map;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;

/**
 * Errors the servlet container renders itself (e.g. {@code sendError(401)} from a security filter) get the
 * same {@code {"message": "..."}} body as controller errors: the status reason phrase, never the path,
 * timestamp or exception.
 */
@Configuration(proxyBeanMethods = false)
class ErrorBodyConfig {

    @Bean
    ErrorAttributes errorAttributes() {
        return new DefaultErrorAttributes() {
            @Override
            public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
                Object status = request.getAttribute("jakarta.servlet.error.status_code", RequestAttributes.SCOPE_REQUEST);
                HttpStatus resolved = status instanceof Integer code ? HttpStatus.resolve(code) : null;
                return Map.of("message", ApiError.of(resolved != null ? resolved : HttpStatus.INTERNAL_SERVER_ERROR)
                        .message());
            }
        };
    }
}
