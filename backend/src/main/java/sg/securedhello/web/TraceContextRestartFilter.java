package sg.securedhello.web;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * Restarts every inbound trace at the application boundary (ADR-063). Registered ahead of Boot's observation filter,
 * it hides the trace-context headers from everything downstream, so each request starts a new trace with a
 * server-generated id: no caller can choose the {@code trace.id} the audit stream joins on, force sampling with a
 * {@code -01} flag, or send baggage. Nothing is parsed, so no header value is ever validated or logged.
 */
public class TraceContextRestartFilter implements Filter {

    /** The fixed strip set: W3C, B3 single and multi header, and W3C baggage. Matched case-insensitively. */
    public static final List<String> STRIPPED_HEADERS = List.of("traceparent", "tracestate", "b3", "X-B3-TraceId",
            "X-B3-SpanId", "X-B3-ParentSpanId", "X-B3-Sampled", "X-B3-Flags", "baggage");

    private final Set<String> stripped;

    /** @param propagatorFields the configured propagators' own header names, read at startup and stripped too */
    public TraceContextRestartFilter(Collection<String> propagatorFields) {
        this.stripped = Stream.concat(STRIPPED_HEADERS.stream(), propagatorFields.stream())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        chain.doFilter(request instanceof HttpServletRequest http ? new StrippedRequest(http) : request, response);
    }

    private boolean isStripped(String name) {
        return name != null && stripped.contains(name.toLowerCase(Locale.ROOT));
    }

    /** The request as if the trace-context headers had never been sent. */
    private final class StrippedRequest extends HttpServletRequestWrapper {

        StrippedRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            return isStripped(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return isStripped(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            return Collections.enumeration(Collections.list(super.getHeaderNames()).stream()
                    .filter(name -> !isStripped(name))
                    .toList());
        }

        @Override
        public long getDateHeader(String name) {
            return isStripped(name) ? -1 : super.getDateHeader(name);
        }

        @Override
        public int getIntHeader(String name) {
            return isStripped(name) ? -1 : super.getIntHeader(name);
        }
    }
}
