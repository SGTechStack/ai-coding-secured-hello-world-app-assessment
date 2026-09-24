package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Asserts the in-request effect of the controller-driven login: after
 * {@code AuthenticationManager.authenticate} succeeds, the controller must
 * place the authenticated {@link Authentication} into the
 * {@link SecurityContextHolder} (not only persist it via the context
 * repository). Dropping that call leaves the holder on the empty anonymous
 * context for the remainder of the request — invisible at the HTTP seam but
 * wrong for any post-auth processing (audit, downstream interceptors, ticket
 * 11's listeners). A {@code postHandle} interceptor observes it directly.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginContextHolderTests extends ApiTestSupport {

    @TestConfiguration
    static class HolderCapture implements WebMvcConfigurer {

        static volatile Authentication captured;

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new HandlerInterceptor() {
                @Override
                public void postHandle(
                        jakarta.servlet.http.HttpServletRequest request,
                        jakarta.servlet.http.HttpServletResponse response,
                        Object handler,
                        org.springframework.web.servlet.ModelAndView modelAndView) {
                    captured = SecurityContextHolder.getContext()
                        .getAuthentication();
                }
            });
        }
    }

    @BeforeEach
    void cleanUsers() {
        HolderCapture.captured = null;
        userRepository.deleteAll();
    }

    @Test
    void loginLeavesAuthenticatedPrincipalInContextHolder() throws Exception {
        seedUser("alice", "alice@example.com");

        login("alice", VALID_PASSWORD).andExpect(status().isOk());

        Authentication captured = HolderCapture.captured;
        assertNotNull(captured,
            "login must populate SecurityContextHolder for the rest of the request");
        assertThat(captured.isAuthenticated()).isTrue();
        assertThat(captured.getName()).isEqualTo("alice");
    }
}
