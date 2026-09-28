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
    static CorsFilter filter(OriginsProperties origins, ProblemDetailWriter writer) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration(origins));
        CorsFilter filter = new CorsFilter(source);
        filter.setCorsProcessor(new ProblemCorsProcessor(writer));
        return filter;
    }

    /** Refuses a CORS request with 403 {@code ACCESS_DENIED} in the envelope. */
    private static final class ProblemCorsProcessor extends DefaultCorsProcessor {

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

        /** Leaves the refusal body to {@link #processRequest}. */
        @Override
        protected void rejectRequest(ServerHttpResponse response) {
        }
    }
}
