package com.assessment.securedhelloworld.config;

import com.assessment.securedhelloworld.security.AppUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;

/**
 * Plain Spring MVC gate (deliberately NOT part of the Spring Security filter chain in
 * {@code SecurityConfig}): it runs after Spring Security has already authenticated and
 * role-checked the request, so a gated admin gets this specific, distinguishable
 * {@code PASSWORD_CHANGE_REQUIRED} body instead of the generic {@code FORBIDDEN} 403 a non-admin
 * gets from Spring Security's own access-denied handler (PRD Story 12 / IM8 ac-6).
 */
@Configuration
public class AdminAccessConfig implements WebMvcConfigurer {

    private final ObjectMapper objectMapper;

    public AdminAccessConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ForcePasswordChangeInterceptor(objectMapper)).addPathPatterns("/api/admin/**");
    }

    static class ForcePasswordChangeInterceptor implements HandlerInterceptor {

        private final ObjectMapper objectMapper;

        ForcePasswordChangeInterceptor(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof AppUserDetails principal
                    && principal.getUser().isForcePasswordChange()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                objectMapper.writeValue(response.getWriter(), Map.of(
                        "error", "PASSWORD_CHANGE_REQUIRED",
                        "message", "You must change your password before continuing"));
                return false;
            }
            return true;
        }
    }
}
