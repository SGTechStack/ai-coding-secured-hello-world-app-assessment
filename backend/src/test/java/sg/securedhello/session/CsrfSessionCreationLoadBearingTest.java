package sg.securedhello.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

import sg.securedhello.security.csrf.CsrfController;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SessionRows;

/**
 * The {@code getSession(true)} in the token bootstrap is load-bearing (ADR-040; R-CSRF-001): with that one call
 * removed, T-SES-026's positive case fails, because nothing else on the route creates a session.
 *
 * <p>This class deliberately starts its own context: it adds a filter, just ahead of the dispatcher, whose request
 * wrapper turns {@code getSession(true)} into {@code getSession(false)} when, and only when, {@link CsrfController}
 * itself is the caller. Every other caller on the route, the token repository included, is left as it is.
 */
@Import(CsrfSessionCreationLoadBearingTest.WithoutTheControllersSessionCreation.class)
class CsrfSessionCreationLoadBearingTest extends CtxDefaultTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class WithoutTheControllersSessionCreation {

        @Bean
        FilterRegistrationBean<Filter> withoutTheControllersSessionCreation() {
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(new RemoveCreation());
            // After Spring Session's and Spring Security's filters, so this wrapper is the one the controller sees.
            registration.setOrder(0);
            return registration;
        }
    }

    /** Wraps each request so the controller's own {@code getSession(true)} creates nothing. */
    static final class RemoveCreation implements Filter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            chain.doFilter(new HttpServletRequestWrapper((HttpServletRequest) request) {
                @Override
                public HttpSession getSession(boolean create) {
                    return super.getSession(create && !calledFromTheController());
                }
            }, response);
        }

        private static boolean calledFromTheController() {
            return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                    .walk(frames -> frames.dropWhile(frame -> frame.getDeclaringClass() == RemoveCreation.class
                            || HttpServletRequest.class.isAssignableFrom(frame.getDeclaringClass())).findFirst())
                    .map(frame -> frame.getDeclaringClass() == CsrfController.class).orElse(false);
        }
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @Proves("T-SES-027")
    void withoutTheControllersGetSessionTrueTheTokenFetchCreatesNoSession() throws Exception {
        SessionRows sessions = new SessionRows(jdbc);
        Set<String> before = sessions.ids();

        MockHttpServletResponse response = mockMvc.perform(get("/api/csrf")).andReturn().getResponse();

        assertThat(response.getStatus()).as("the fetch itself still answers").isEqualTo(200);
        assertThat(sessions.ids()).as("session rows after the token fetch").isEqualTo(before);
        assertThat(response.getCookie("SESSION")).as("session cookie").isNull();
    }
}
