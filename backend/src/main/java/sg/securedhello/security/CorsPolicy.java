package sg.securedhello.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import sg.securedhello.config.OriginsProperties;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;

/**
 * CORS for the SPA (ADR-036; ADR-059): its one origin, with credentials, so the session cookie travels, and the
 * {@code X-CSRF-TOKEN} header, which makes every mutation a preflight. Nothing else is allowed, and no wildcard.
 */
final class CorsPolicy {

    private CorsPolicy() {
    }

    /** The one configuration, applied to every path. */
    static CorsConfiguration configuration(OriginsProperties origins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(origins.spa()));
        configuration.setAllowCredentials(true);
        configuration.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.ACCEPT, HttpHeaders.CONTENT_TYPE, "X-CSRF-TOKEN"));
        return configuration;
    }

    /** The CORS filter, whose refusal is the shared envelope rather than the framework's plain-text body (ADR-031). */
    static CorsFilter filter(CorsConfiguration configuration, ProblemDetailWriter writer) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        CorsFilter filter = new CorsFilter(source);
        filter.setCorsProcessor(new ProblemCorsProcessor(writer));
        return filter;
    }

    /**
     * Adds the CORS response headers to a response written before {@code CorsFilter} runs, such as the early
     * limiter's 429, when the request comes from the allowed origin. Anything else is left alone: the response is a
     * refusal already, and a disallowed origin simply cannot read it.
     */
    static void allowOrigin(CorsConfiguration configuration, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        new HeadersOnlyCorsProcessor().processRequest(configuration, request, response);
    }

    /** The framework's processor, minus its own refusal body. */
    private static class HeadersOnlyCorsProcessor extends DefaultCorsProcessor {

        @Override
        protected void rejectRequest(ServerHttpResponse response) {
        }
    }

    /** Refuses a CORS request with 403 {@code ACCESS_DENIED} in the envelope. */
    private static final class ProblemCorsProcessor extends HeadersOnlyCorsProcessor {

        private final ProblemDetailWriter writer;

        ProblemCorsProcessor(ProblemDetailWriter writer) {
            this.writer = writer;
        }

        @Override
        public boolean processRequest(CorsConfiguration configuration, HttpServletRequest request,
                HttpServletResponse response) throws IOException {
            boolean accepted = super.processRequest(configuration, request, response);
            if (!accepted) {
                writer.write(request, response, ErrorCode.ACCESS_DENIED);
            }
            return accepted;
        }
    }
}
